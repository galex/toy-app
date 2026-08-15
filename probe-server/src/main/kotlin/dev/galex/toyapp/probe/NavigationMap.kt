package dev.galex.toyapp.probe

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The app's navigation graph, declared once by the app and served over GET /nav_map.
 *
 * This is what stops an agent from rediscovering our app on every edit. Instead of dumping the UI
 * to work out where things are, it asks for the map, finds the screen that owns the element it
 * cares about, and walks straight there.
 */
data class NavigationMap(val screens: List<Screen>) {

    /** The screen the app opens on, which is where every route starts. */
    val entry: Screen? get() = screens.firstOrNull { it.entry }

    fun screen(id: String): Screen? = screens.firstOrNull { it.id == id }
}

/**
 * One screen of the app.
 *
 * [breadcrumb] is what the app reports through [ProbeHooks.breadcrumb] once we are here, and may
 * carry `{placeholders}` for the parts that depend on data, so `goto` can check where it landed
 * without pinning the check to one toy.
 */
data class Screen(
    val id: String,
    val breadcrumb: String,
    val entry: Boolean = false,
    val ids: List<String> = emptyList(),
    val actions: List<Action> = emptyList(),
)

/** Tapping [tapId] on the screen that declares this action lands us on the screen [leadsTo]. */
data class Action(
    val tapId: String,
    val leadsTo: String,
)

/** Serialized by hand, like every other probe payload, so no compiler plugin is needed here. */
internal fun NavigationMap.toJson(): JsonObject = buildJsonObject {
    put(
        "screens",
        JsonArray(
            screens.map { screen ->
                buildJsonObject {
                    put("id", JsonPrimitive(screen.id))
                    put("breadcrumb", JsonPrimitive(screen.breadcrumb))
                    put("entry", JsonPrimitive(screen.entry))
                    put("ids", JsonArray(screen.ids.map { JsonPrimitive(it) }))
                    put(
                        "actions",
                        JsonArray(
                            screen.actions.map { action ->
                                buildJsonObject {
                                    put("tapId", JsonPrimitive(action.tapId))
                                    put("leadsTo", JsonPrimitive(action.leadsTo))
                                }
                            },
                        ),
                    )
                }
            },
        ),
    )
}
