package dev.galex.toyapp.probe

import android.app.Application

/**
 * Everything the server needs to know about the app it lives in.
 *
 * One static port per app and per flavor, so several apps can run at once without a race. The
 * server never falls back to another port: a probe that silently moved is a probe the CLI can't
 * find.
 */
data class ProbeConfig(
    val appName: String,
    val versionName: String,
    val packageName: String,
    val port: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 4242
    }
}

/**
 * App-supplied callbacks, so the probe module stays free of anything app-specific.
 *
 * [breadcrumb] tells us where we are ("Toys > ToyDetail"), which is what a flow should assert on:
 * it survives a translation, while visible copy does not.
 *
 * [navigationMap] hands over every screen of the app, the ids it owns and the taps that lead out of
 * it, so the CLI can route to a screen instead of making the agent discover the way there.
 */
class ProbeHooks(
    val breadcrumb: () -> String = { "" },
    val navigationMap: () -> NavigationMap? = { null },
)

/** One visible thing on screen, with the bounds a tap can aim at. */
data class UiElement(
    val id: String?,
    val text: String?,
    val role: String?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val clickable: Boolean,
)

interface ElementSource {
    fun snapshot(): List<UiElement>
}

interface ProbeDriver {
    fun tap(x: Float, y: Float)
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300)
    fun typeText(text: String)
    fun pressBack()

    /** PNG-encoded screenshot of what is on screen right now. */
    fun screenshot(): ByteArray

    /** width to height, in pixels. */
    fun displaySize(): Pair<Int, Int>
}

class ToolContext(
    val config: ProbeConfig,
    val driver: ProbeDriver,
    val elementSource: ElementSource,
    val hooks: ProbeHooks,
    val logStream: LogStream,
)

/**
 * Starts the probe HTTP server on 127.0.0.1:[ProbeConfig.port].
 *
 * Call it once, from a debug-only `Application.onCreate`. There is no release twin of this call by
 * design: the module arrives through `debugImplementation`, so in a release build this function
 * does not exist at all.
 */
fun startProbe(
    application: Application,
    config: ProbeConfig,
    hooks: ProbeHooks = ProbeHooks(),
): ProbeServer {
    val activityTracker = CurrentActivityTracker()
    application.registerActivityLifecycleCallbacks(activityTracker)
    val context = ToolContext(
        config = config,
        driver = AndroidProbeDriver(activityTracker),
        elementSource = SemanticsElementSource(activityTracker),
        hooks = hooks,
        logStream = LogStream(),
    )
    return ProbeServer(context).apply { start() }
}
