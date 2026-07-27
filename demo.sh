#!/usr/bin/env bash
# Build, install, launch, forward, and run the flow. One command, so the whole loop is recordable.
set -euo pipefail

cd "$(dirname "$0")"

PACKAGE="dev.galex.toyapp.debug"
ACTIVITY="dev.galex.toyapp.MainActivity"
PORT="${PORT:-4242}"

if ! adb devices | grep -qE "device$"; then
  echo "No device or emulator. Start one, then run this again." >&2
  exit 1
fi

echo "==> building the debug APK"
./gradlew --quiet :app:assembleDebug

echo "==> installing"
adb install -r app/build/outputs/apk/debug/app-debug.apk > /dev/null

echo "==> launching"
adb shell am start -n "$PACKAGE/$ACTIVITY" > /dev/null

echo "==> forwarding tcp:$PORT"
adb forward "tcp:$PORT" "tcp:$PORT" > /dev/null

# Poll rather than sleep: the app needs a moment, and how long depends on the machine.
echo "==> waiting for the probe to answer"
for _ in $(seq 1 30); do
  if curl -sf "http://127.0.0.1:$PORT/app_info" -o /dev/null; then break; fi
  sleep 1
done
curl -sf "http://127.0.0.1:$PORT/app_info" -o /dev/null || {
  echo "probe never answered on port $PORT" >&2
  exit 1
}

echo "==> running the flow"
exec probe/scripts/run-flow probe/flows/open-a-toy.yaml \
  --screenshot-every-step \
  --junit-xml probe-artifacts/junit/open-a-toy.xml
