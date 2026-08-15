package dev.galex.toyapp.probe

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** One analytics event, exactly as the app fired it. */
data class RecordedEvent(val name: String, val params: Map<String, String>)

/**
 * Every analytics event the debug build fired, in order.
 *
 * Analytics is the least tested code most apps ship: it leaves for a vendor SDK and nobody ever
 * asserts on it, so a duplicated event survives for months and only ever shows up as a dashboard
 * that looks a bit too good. Recording events here lets a flow assert on them, counts included.
 *
 * Debug only, like the rest of this module, and bounded, because a dev tool that grows without end
 * is a dev tool that eventually takes the app down with it.
 */
object AnalyticsRecorder {

    private const val MAX_EVENTS = 500

    private val events = ArrayDeque<RecordedEvent>()

    @Synchronized
    fun record(name: String, params: Map<String, String>) {
        if (events.size == MAX_EVENTS) events.removeFirst()
        events.addLast(RecordedEvent(name, params))
    }

    @Synchronized
    fun snapshot(): List<RecordedEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}

internal fun List<RecordedEvent>.toJson(): JsonObject = buildJsonObject {
    put(
        "events",
        JsonArray(
            map { event ->
                buildJsonObject {
                    put("name", JsonPrimitive(event.name))
                    put(
                        "params",
                        JsonObject(event.params.mapValues { (_, value) -> JsonPrimitive(value) }),
                    )
                }
            },
        ),
    )
}
