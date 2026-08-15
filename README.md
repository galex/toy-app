# Toy App

A tiny Android app that exists to prove one thing: **a coding agent can drive a running app by
itself**, read what is actually on screen, and leave a test behind.

It has two screens. A list of toys, and a toy detail screen. That is all, on purpose, because every
technique here has to be visible in one screenshot.

```
app/              two Compose screens and a six-item list of toys
automation-ids/   Modifier.automationId, so every element has a stable, unique id
probe-server/     a debug-only HTTP server inside the app, reading Compose's semantics tree
probe/scripts/    the CLI an agent drives, and the YAML flow runner (standard-library Python 3)
probe/flows/      open the list, open a toy, come back, with and without the navigation map
```

> **This branch, `post/navigation-map`,** adds the navigation map: the app declares every screen,
> its ids and its exits in Kotlin, the probe serves them on `GET /nav_map`, and the CLI walks them,
> so an agent stops rediscovering the app on every edit. See **Navigating without looking** below.

## Run the demo

An emulator or a device, and nothing else. No `pip install`, no virtualenv, no build step for the
tooling.

```bash
./demo.sh
```

That builds the debug APK, installs it, launches it, forwards the port, and runs the flow. The last
run of it looked like this:

```
flow 'open a toy': 13 step(s) against http://127.0.0.1:4242
  ok   [0] assert_id 'toys_title' ok  (24ms)
  ok   [1] assert_text 'Wooden Train' ok  (10ms)
  ok   [2] screenshot -> probe-artifacts/01-toy-list.png  (139ms)
  ok   [3] tap_id 'toys_index_2_card' -> (640,948)  (59ms)
  ok   [4] assert_breadcrumb 'ToyDetail(building-blocks)' ok (at 'Toys > ToyDetail(building-blocks)')  (12ms)
  ok   [5] assert_id 'toy_detail_name' ok  (7ms)
  ok   [6] assert_text 'Building Blocks' ok  (6ms)
  ok   [7] assert_text 'Construction' ok  (6ms)
  ok   [8] screenshot -> probe-artifacts/02-toy-detail.png  (123ms)
  ok   [9] tap_id 'toy_detail_back_button' -> (262,588)  (11ms)
  ok   [10] assert_breadcrumb 'Toys' ok (at 'Toys')  (8ms)
  ok   [11] assert_no_text 'under the sofa' ok  (8ms)
  ok   [12] screenshot -> probe-artifacts/03-back-on-list.png  (122ms)
flow 'open a toy' passed in 2.4s
```

Thirteen steps, three screenshots, **2.4 seconds**, no human looking at anything.

## Drive it by hand

Once the app is running and the port is forwarded, this is the whole vocabulary:

```bash
probe/scripts/forward.sh              # once, after installing
probe/scripts/probe app-info          # is it reachable?
probe/scripts/probe ui-snapshot       # ids, text and bounds of everything on screen
probe/scripts/probe tap-id toys_index_2_card
probe/scripts/probe screenshot -o /tmp/after.png
probe/scripts/probe logs
```

`ui-snapshot` is the one that matters. A screenshot makes an agent guess coordinates, while this
makes it certain:

```json
{
  "id": "dev.galex.toyapp:id/toys_index_2_card",
  "text": null, "role": "Button",
  "x": 32.0, "y": 858.0, "width": 1216.0, "height": 180.0,
  "clickable": true
}
```

## Navigating without looking

Dumping the whole UI is the right way to ask "what is on this screen", and the wrong way to ask
"where is that screen and how do I get there". The second question has a fixed answer, so the app
writes it down once, in `app/src/debug/kotlin/dev/galex/toyapp/AppNavigationMap.kt`, and the probe
serves it:

```bash
probe/scripts/probe nav-map                    # every screen, its ids, its exits
probe/scripts/probe owner-of toy_detail_name   # which screen owns this id?
probe/scripts/probe goto toy_detail --index 2  # walk there, checking the breadcrumb at each hop
```

```
goto 'toy_detail': 1 hop(s) from 'Toys'
  ok   tap_id 'toys_index_2_card' -> (640,948)
  ok   arrived at 'Toys > ToyDetail(building-blocks)'
```

The ids in the map are not strings written a second time. They come from the same `ToysIds` and
`ToyDetailIds` constants the composables pass to `Modifier.automationId`, so renaming one breaks the
build instead of quietly sending the agent to a tap that lands nowhere.

What the compiler cannot check is the arrows: nothing stops us from declaring that a tap leads to a
screen it doesn't. So the runner walks every edge of the map on a real device, which is what belongs
in CI after any navigation change:

```
$ probe/scripts/run-flow --from-map --index 3
flow 'navigation map': 6 step(s) against http://127.0.0.1:4242
  ok   [0] goto 'toys' -> 0 hop(s), at 'Toys'  (17ms)
  ok   [1] tap_id 'toys_index_3_card' -> (640,1224)  (10ms)
  ok   [2] assert_breadcrumb 'ToyDetail' ok (at 'Toys > ToyDetail(spinning-top)')  (166ms)
  ok   [3] goto 'toy_detail' -> 0 hop(s), at 'Toys > ToyDetail(spinning-top)'  (14ms)
  ok   [4] tap_id 'toy_detail_back_button' -> (262,588)  (9ms)
  ok   [5] assert_breadcrumb 'Toys' ok (at 'Toys')  (167ms)
flow 'navigation map' passed in 0.4s
```

A flow can say where it wants to be instead of spelling out the taps that get there, which is what
`probe/flows/open-a-toy-with-goto.yaml` does. Insert a screen in the middle of that path tomorrow,
and the flow still passes.

`.claude/skills/app-navigation/SKILL.md` is what makes an agent reach for all of this without being
asked. It lives in a skill rather than in `CLAUDE.md` because `CLAUDE.md` is loaded on every turn,
including the many that never touch the UI.

## How it works

**The ids** come from `Modifier.automationId("card")` inside an `AutomationContext("toys")` and an
`AutomationIndex(index)` scope, which is what turns six identical rows into `toys_index_0_card`
through `toys_index_5_card`. The app sets `testTagsAsResourceId = true` once near the root, without
which the tags never reach anything outside the app.

**The snapshot** reads Compose's own semantics tree, the same one Compose UI tests read: find the
`ViewRootForTest` under the decor view, take `unmergedRootSemanticsNode` (merged would collapse
every child id into its nearest clickable ancestor), and report `boundsInWindow`, because that is
the exact space taps are dispatched into.

**The input** is synthesized `MotionEvent`s with a shared `downTime`, interpolated `ACTION_MOVE`s,
and text typed through `KeyCharacterMap` so the real IME path runs.

**The breadcrumb** is how a flow asserts where it is (`Toys > ToyDetail(building-blocks)`) instead
of asserting on visible copy, which breaks the day someone ships a translation.

**No `sleep` step exists.** Every assertion polls for up to 4 seconds, and a transient error while
Compose is mid-recomposition counts as "not yet" rather than as a failure.

## It cannot ship by accident

The probe is wired in with `debugImplementation`, and `ProbeStarter.kt` has a real implementation in
`src/debug` and a no-op twin in `src/release`. The `INTERNET` permission it needs lives in the
**debug** manifest.

So the release build does not have the probe disabled, it does not contain it:

```bash
./gradlew :app:assembleRelease
unzip -p app/build/outputs/apk/release/app-release-unsigned.apk classes.dex | strings | grep -c "dev/galex/toyapp/probe"
# 0
```

## Two traps worth knowing

**Opening a socket needs `INTERNET`, even for `127.0.0.1`.** Without it the server dies with
`SocketException: Operation not permitted`.

**Ktor's CIO engine binds asynchronously**, so a `BindException` never reaches the `try/catch`
around `start()`. It lands on the default uncaught handler and kills the app. The server therefore
runs in a `CoroutineScope(SupervisorJob() + CoroutineExceptionHandler)` and passes that scope as
`parentCoroutineContext`. When the socket failed on the first run of this very project, the app
stayed up and logged one clear line, which is the entire point.
