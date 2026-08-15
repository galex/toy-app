package dev.galex.toyapp.analytics

import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The one way this app fires an analytics event.
 *
 * Going through an interface is what makes the events testable at all: the debug build can wrap it
 * (see `recordingIfDebug` in src/debug) and see everything on its way out, without the screens
 * knowing anything about it.
 */
interface Analytics {
    fun track(name: String, params: Map<String, String> = emptyMap())
}

/** Stands in for the vendor SDK. In a real app this is Firebase, Amplitude, or whatever we pay for. */
class LoggingAnalytics : Analytics {
    override fun track(name: String, params: Map<String, String>) {
        Log.i("Analytics", if (params.isEmpty()) name else "$name $params")
    }
}

/** Never null, so a screen can always fire an event, and a preview never crashes. */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoAnalytics }

object NoAnalytics : Analytics {
    override fun track(name: String, params: Map<String, String>) = Unit
}

/** Every event name this app knows how to fire, so a flow and a screen can never disagree. */
object Events {
    const val ToyListShown = "toy_list_shown"
    const val ToyOpened = "toy_opened"

    const val ToyIdParam = "toy_id"
}
