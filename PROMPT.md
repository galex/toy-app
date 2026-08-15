# The probe prompt

This is the full prompt behind [How to Make Our Coding Agent Fully
Independent](https://galex.dev/posts/how-to-make-your-coding-agent-fully-independent/), and its
Phase 7 comes from [How to Give Our Coding Agent a Map of the
App](https://galex.dev/posts/how-to-give-our-coding-agent-a-map-of-the-app/).
Hand it to a coding agent in your own project and it will build the probe described in this
repository: a debug-only HTTP server inside the app, and a CLI on top of it.

It is long because it carries every trap hit while building the original, and each one of them
cost an evening. It works on a plain Android project and on a Kotlin Multiplatform one, because
the first thing it asks is which one you are in.

---

Build me a dev-only "probe" for this app: an HTTP server embedded in the debug build, and a CLI on
top of it, so that you can read and drive the running app yourself instead of asking me to look at
my screen.

## Rules that apply to everything below

- The probe must be IMPOSSIBLE to ship in a release build. Not "guarded by a flag", absent.
- The probe must never crash, freeze or slow down the app it lives in. A dev tool that can take
  down the app it inspects is worse than no dev tool at all.
- Every handler returns JSON. On failure it returns {"ok": false, "error": "..."} with a message
  that tells the caller what to do next. Nothing is allowed to throw out of a handler.
- No new dependency in the app itself beyond Ktor server + kotlinx-serialization in the dev module.
- Work in the order below and STOP where I tell you to stop.

## Phase 0: what kind of project is this?

Before writing anything, look at the build files and tell me which of these we're in:

- **Plain Android**: `com.android.application` / `com.android.library` modules, Kotlin/JVM only, no
  Kotlin Multiplatform plugin.
- **KMP / Compose Multiplatform**: `kotlin("multiplatform")`, `commonMain` / `androidMain` source
  sets, probably a `composeApp` module and desktop and/or iOS targets.

Also tell me whether the screens are **Compose** or **Android Views**, and which module owns the
Application class. Then say which path below you're taking, and only then start Phase 1.

## Phase 1: the module, and /app_info only

Create the module, choosing the shape that matches what you found:

- **Plain Android**: a new `com.android.library` module `probe-server`, Kotlin only, same minSdk as
  the app. Skip everything marked KMP-only below, and skip the desktop driver entirely.
- **KMP / CMP**: a new Kotlin Multiplatform module `probe-server`, targeting ONLY `jvm()` and
  `androidLibrary`. No iOS, no web, deliberately: the target list is the first line of defence,
  since the module then cannot reach a platform we never want it on. Put the routes, the config and
  the interfaces in `commonMain`, and the platform pieces in `androidMain` and `jvmMain`.

Then, in both cases:

- Dependencies: ktor-server-core, ktor-server-cio, kotlinx-coroutines-core,
  kotlinx-serialization-json. Nothing else.
- Wire it into the app with `debugImplementation`, never `implementation`, and start it from a
  `src/debug` source set with a no-op twin in `src/release`. The point is that the code is not in
  the release APK at all.
- Android: the module needs `<uses-permission android:name="android.permission.INTERNET" />` in its
  own manifest, or the server cannot bind a socket even on 127.0.0.1. It is on the debug classpath
  only, so that permission never reaches the release app.
- KMP only: the desktop app has no build types, so start the probe from `main()` guarded by the
  build flavor or an environment variable, never in a production build.
- data class ProbeConfig(appName, flavor, versionName, packageName, platform, port).
- Everything app-specific reaches the routes through a small context object: a driver (input), an
  element source (UI tree), hooks (login/logout/navigation), a log buffer. Nothing app-specific is
  hard-coded in the module.
- Embedded Ktor CIO server, plain JSON, bound to 127.0.0.1 on ProbeConfig.port.
- GET /app_info returns the config as JSON.

Server lifecycle, and this is the part that bit me hardest:

- Ktor's CIO engine binds its socket inside an internal acceptJob, so a BindException surfaces
  ASYNCHRONOUSLY. A try/catch around embeddedServer(...).start() never sees it, the exception
  reaches the default uncaught handler and kills the whole app.
- So build the engine inside a CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { ... })
  and pass that scope's context as `parentCoroutineContext`. Only the CoroutineScope.embeddedServer
  overloads accept it.
- If the port is taken: log loudly, leave the probe disabled for the whole run, and NEVER fall back
  to another port. A probe that silently moved is a probe nothing can find. One static port per app
  and per flavor, defined in exactly one place.

STOP HERE. I'll install a debug build and run `curl -s localhost:<port>/app_info` before you
continue.

If the probe doesn't answer, read logcat before changing anything. Both of the failures above
announce themselves clearly in one line, which is the whole reason the failure handling exists.

## Phase 2: seeing the screen

GET /ui_snapshot returns every visible element as
{id, text, role, x, y, width, height, clickable}.

Read Compose's OWN semantics tree, the same one Compose UI tests read:

- from the current Activity's decorView, recursively find the child that is a ViewRootForTest
- take semanticsOwner.unmergedRootSemanticsNode. UNMERGED matters: the merged tree collapses a
  clickable subtree into one node and every child id disappears into its nearest clickable ancestor
- recurse node.children, reading TestTag, Text ?: ContentDescription, Role, OnClick
- use node.boundsInWindow for the bounds, because that is the exact coordinate space the tap
  injection of Phase 3 dispatches MotionEvents into. Mixing it with boundsInRoot produces taps that
  land next to the target and an agent that concludes the app is broken.

Do NOT walk View.createAccessibilityNodeInfo(): outside a real AccessibilityService the root node
comes back un-sealed, reading its bounds throws "not sealed instance", and virtual children never
resolve without an accessibility connection.

Track the current Activity with Application.ActivityLifecycleCallbacks holding a
WeakReference<Activity>, so the element source and the driver always target what is on screen.

Ids come from Modifier.testTag, and Android needs `testTagsAsResourceId = true` set once near the
root of the composable tree. If this app has no test tags yet, tell me, and propose a hierarchical
scheme (screen > index > element) rather than tagging things at random.

If the screens are **Android Views** and not Compose, walk the View hierarchy from the decorView
instead: skip anything not visible, read the id through
`resources.getResourceEntryName(view.id)`, the text from TextView, the description from
contentDescription, the bounds from `getLocationInWindow` plus width and height, and clickability
from `isClickable`. Everything else in this phase stays exactly the same. If the app is mixed, walk
the View tree and descend into the Compose semantics tree whenever you meet a ComposeView.

Also expose GET /backstack returning where we are in the app as a breadcrumb, for example
"Home > RecipeDetail", read from the navigator. Assertions on a breadcrumb survive translation,
assertions on visible copy do not.

Cross-window is out of scope for this phase: a dialog, a popup or a bottom sheet that opens its own
window is a separate composition and will not appear. When we need it, we'll add a separate
debug-only AccessibilityService and read the nodes it exposes for every window, keeping this
semantics dump as the default because it is richer and needs nothing enabled by hand.

## Phase 3: driving the screen

One interface, implemented per platform:

interface ProbeDriver {
    fun tap(x: Float, y: Float)
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long)
    fun inputText(text: String)
    fun pressBack()
    fun screenshot(): ByteArray
    fun displaySize(): Pair<Int, Int>
}

On Android:

- tap is a swipe with durationMs = 0, so there is a single gesture path to get right
- swipe: MotionEvent.obtain with a downTime SHARED by every event of the gesture, ACTION_DOWN, then
  roughly one interpolated ACTION_MOVE per 16ms, then ACTION_UP. Dispatch to decorView on the main
  handler and recycle() every event. A per-event downTime is read as three unrelated events, and
  skipping the MOVEs turns a fling into a teleport that scrolls nothing.
- inputText: KeyCharacterMap.load(VIRTUAL_KEYBOARD).getEvents(text) dispatched to
  decorView.findFocus(). Go through the real IME path so onValueChange, filters and validation all
  run. Do not set the text field's value directly, that tests nothing.
- screenshot: PixelCopy.request on the activity window, CountDownLatch with a 2s timeout, then
  compress to PNG. PixelCopy captures what the GPU actually composited, including video and
  surfaces.
- pressBack: dispatch the platform back.

KMP only, and skip this on a plain Android project: on JVM desktop, implement the same interface
with java.awt.Robot (mouseMove + mousePress/Release,
KeyEvent.getExtendedKeyCodeForChar, robot.createScreenCapture(window.bounds)), translating
coordinates through window.locationOnScreen. pressBack is an honest no-op there.

Routes for this phase: POST /tap {x,y}, POST /swipe {startX,startY,endX,endY,durationMs},
POST /input_text {text}, POST /press_back, GET /screenshot returning raw image/png bytes,
GET /logs returning the last N lines from the app's logger, and POST /login + POST /logout wired to
the app-specific hooks so a run can start from a known state.

## Phase 4: the CLI I actually drive

Write it under probe/scripts/, standard-library Python 3 ONLY. No pip install, no node_modules, no
build step. Every setup step is a step that can go wrong on a fresh clone or on a CI image.

- probe_client.py: one method per endpoint over urllib.request, and the SINGLE source of truth for
  the port map. Everything else, including any shell script, resolves ports through it.
  Treat urllib HTTPError as a normal response, since a 400 still carries our JSON body and the
  caller needs to read it. On URLError, raise an error whose message says exactly what to do next,
  for example "cannot reach probe at ... Is a debug build running, and did you run forward.sh?".
  Write every error message for the agent reading it, not for a human who already knows the setup.
- probe: argparse, global --app/--flavor/--host/--port/--timeout, one subcommand per endpoint:
  app-info, ui-snapshot, screenshot -o, tap --x --y, swipe, input-text --text, press-back,
  backstack, logs, login, logout. JSON to stdout with indent=2, errors as
  {"ok": false, "error": ...} on stderr with exit code 1.
- forward.sh: `adb forward tcp:<port> tcp:<port>`, then curl /app_info and print a hint instead of a
  stack trace when it fails. Support multiple devices through HOST_PORT and SERIAL.

## Phase 5: from a session to a suite

Add a YAML flow runner next to the CLI, same standard-library Python 3 rule, so that everything you
verified by hand survives the session:

- A flow is a name, a port, and a list of steps: assert_id, assert_text, assert_no_text,
  assert_breadcrumb, tap_id, input_text, press_back, screenshot.
- tap_id resolves the id through /ui_snapshot and taps the CENTRE of the bounds it just read, so a
  flow never carries a hard-coded coordinate.
- Support --screenshot-every-step and --junit-xml, so the same flow that guided development runs on
  an emulator in CI and reports as a normal test.

## Phase 6: keep it switched on

Add this to CLAUDE.md (or your equivalent), so you reach for it without being asked:

    ## Driving the running app

    A debug build exposes a probe HTTP server, driven by probe/scripts/probe.

        probe/scripts/forward.sh              # once, for Android
        probe/scripts/probe app-info          # confirm it's reachable
        probe/scripts/probe ui-snapshot       # ids, text, bounds of everything on screen
        probe/scripts/probe tap --x 540 --y 310
        probe/scripts/probe input-text --text "Pancakes"
        probe/scripts/probe screenshot -o /tmp/after.png

    Rules:
    - After any UI change, install the app and verify with ui-snapshot before saying it's done
    - Never tap coordinates you didn't just read from a snapshot
    - If you can't reach the probe, run forward.sh once, then say so, don't guess

## Phase 7: a map, so you stop rediscovering the app

A snapshot answers "what is on this screen". It is the wrong tool for "where is that screen and how
do I get there", and you were paying for a full UI dump every time you asked that. The second answer
never changes, so let the app declare it once:

- In the probe module: `NavigationMap(screens)`, `Screen(id, breadcrumb, entry, ids, actions)` and
  `Action(tapId, leadsTo)`. Plain data classes, serialized by hand like every other payload here.
- The app's own map is ONE static object in the `src/debug` source set, handed to the probe through
  a hook next to the breadcrumb one. There is no navigation map in a release build.
- The ids in it MUST come from the same constants the composables pass to `Modifier.automationId`.
  If those are string literals today, extract them into a `<Screen>Ids` object first. This is the
  whole point of the map being code: renaming an id then breaks the build, instead of quietly
  sending you to a tap that lands nowhere.
- Breadcrumbs carry `{placeholders}` for the parts that depend on data, so a check matches the shape
  of a screen and not one row of it.
- Serve it on GET /nav_map, and add `nav-map`, `owner-of <id>` and `goto <screen>` to the CLI, where
  goto searches the `leadsTo` graph from the screen you are ON, taps its way there, and checks the
  breadcrumb at every hop. Match the LAST part of the breadcrumb trail, never anywhere inside it, or
  "Toys" passes while you are still on "Toys > ToyDetail(...)".
- Add a `goto` step to the flow runner, and a `--from-map` mode that walks every edge on a device.
  The compiler checks the ids, only a device checks the arrows.

Then put the rules in a SKILL (`.claude/skills/app-navigation/SKILL.md`) rather than in CLAUDE.md,
which is loaded on every single turn including the many that never touch the UI: read the map before
any tap, and update the map in the same edit that changes the navigation.

## Definition of done

From a fresh clone: install a debug build, run forward.sh, and drive a full screen end to end with
the CLI alone. `probe ui-snapshot` returns real ids with real bounds, `probe tap` lands on the
element those bounds describe, `probe nav-map` returns every screen with its real ids and
`probe goto <screen>` arrives on it, one YAML flow passes, and no release variant contains a single
line of probe code.
