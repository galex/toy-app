package dev.galex.toyapp.probe

import android.util.Log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

private const val TAG = "Probe"

/**
 * A dev-only, plain HTTP control server embedded in the debug build. Every capability is one
 * curl-able JSON endpoint (see [registerProbeRoutes]).
 */
class ProbeServer(private val context: ToolContext) {

    private var server: EmbeddedServer<*, *>? = null

    /**
     * Owns the engine's coroutines, so the probe can never take the host app down.
     *
     * CIO binds its socket inside an internal `acceptJob`, so a BindException surfaces
     * ASYNCHRONOUSLY and [start]'s try/catch never sees it. Without the [CoroutineExceptionHandler]
     * below it reaches the thread's default uncaught handler, which on Android is a FATAL EXCEPTION
     * that kills the app. The [SupervisorJob] keeps that failure from cancelling anything else.
     */
    private val engineScope = CoroutineScope(
        SupervisorJob() + CoroutineExceptionHandler { _, t -> onEngineFailure(t) },
    )

    private fun onEngineFailure(t: Throwable) {
        Log.e(
            TAG,
            "probe could not bind 127.0.0.1:${context.config.port}, so it stays disabled for this " +
                "run (is another instance already using this port?)",
            t,
        )
        context.logStream.stop()
    }

    fun start() {
        if (server != null) return
        context.logStream.start()

        server = try {
            // Only the CoroutineScope.embeddedServer(...) overloads accept a parentCoroutineContext,
            // which is the whole point: it routes the engine's async bind failure to engineScope's
            // handler instead of to the process.
            engineScope.embeddedServer(
                CIO,
                host = "127.0.0.1",
                port = context.config.port,
                parentCoroutineContext = engineScope.coroutineContext,
            ) {
                routing { registerProbeRoutes(context) }
            }.also { it.start(wait = false) }
        } catch (t: Throwable) {
            // Synchronous failures (bad config, engine construction) still land here.
            onEngineFailure(t)
            null
        }

        if (server != null) {
            Log.i(TAG, "probe listening on 127.0.0.1:${context.config.port}")
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 200)
        server = null
        engineScope.coroutineContext[Job]?.cancelChildren()
        context.logStream.stop()
    }
}

/** Ring buffer of the last [capacity] log lines, so the agent can read logcat without adb. */
class LogStream(private val capacity: Int = 500) {

    private val entries = ArrayDeque<String>()

    fun start() = ProbeLog.register { line ->
        synchronized(entries) {
            entries.addLast(line)
            if (entries.size > capacity) entries.removeFirst()
        }
    }

    fun stop() = ProbeLog.unregister()

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }
}

/** The app logs through here, and the probe reads it back out through GET /logs. */
object ProbeLog {

    private var sink: ((String) -> Unit)? = null

    fun register(sink: (String) -> Unit) {
        this.sink = sink
    }

    fun unregister() {
        sink = null
    }

    fun log(tag: String, message: String) {
        Log.i(tag, message)
        sink?.invoke("[$tag] $message")
    }
}
