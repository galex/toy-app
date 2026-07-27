#!/usr/bin/env bash
# Forwards the probe port from a connected device or emulator to the host, so the CLI can reach it
# at http://localhost:$PORT. Run it once after installing the debug build.
#
# With several devices plugged in, pick a distinct host port per device and target it by serial:
#   HOST_PORT=5242 SERIAL=emulator-5554 ./probe/scripts/forward.sh
# then point the CLI at that host port: probe/scripts/probe --port 5242 app-info
set -euo pipefail

PORT="${PORT:-4242}"
HOST_PORT="${HOST_PORT:-$PORT}"
SERIAL="${SERIAL:-}"

ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

"${ADB[@]}" forward "tcp:$HOST_PORT" "tcp:$PORT"
echo "forwarded host tcp:$HOST_PORT -> device tcp:$PORT${SERIAL:+ ($SERIAL)}"

if curl -sf "http://localhost:$HOST_PORT/app_info" -o /dev/null; then
  echo "probe is reachable: try probe/scripts/probe --port $HOST_PORT app-info"
else
  echo "probe isn't answering yet. Is the debug build installed and running?" >&2
fi
