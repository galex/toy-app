package dev.galex.toyapp.probe

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

private fun JsonObject?.string(key: String): String? = this?.get(key)?.jsonPrimitive?.contentOrNull
private fun JsonObject?.float(key: String): Float? = this?.get(key)?.jsonPrimitive?.floatOrNull
private fun JsonObject?.long(key: String): Long? = this?.get(key)?.jsonPrimitive?.longOrNull

private suspend fun ApplicationCall.body(): JsonObject? =
    runCatching {
        val text = receiveText()
        if (text.isBlank()) null else json.parseToJsonElement(text) as? JsonObject
    }.getOrNull()

private suspend fun ApplicationCall.respondJson(
    element: JsonElement,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respondText(
    json.encodeToString(JsonElement.serializer(), element),
    ContentType.Application.Json,
    status,
)

private suspend fun ApplicationCall.respondOk() =
    respondJson(buildJsonObject { put("ok", JsonPrimitive(true)) })

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) =
    respondJson(
        buildJsonObject {
            put("ok", JsonPrimitive(false))
            put("error", JsonPrimitive(message))
        },
        status,
    )

/**
 * Registers every probe capability as a plain HTTP endpoint. GET for reads, POST for actions.
 *
 * No handler is allowed to throw. Our caller is an agent, and an agent that receives a stack trace
 * on a dead socket starts inventing theories, while an agent that receives
 * `{"ok": false, "error": "tap requires numeric x and y"}` fixes its call and carries on.
 */
fun Routing.registerProbeRoutes(context: ToolContext) {

    get("/app_info") {
        val config = context.config
        call.respondJson(
            buildJsonObject {
                put("appName", JsonPrimitive(config.appName))
                put("versionName", JsonPrimitive(config.versionName))
                put("packageName", JsonPrimitive(config.packageName))
                put("platform", JsonPrimitive("android"))
                put("port", JsonPrimitive(config.port))
            },
        )
    }

    get("/ui_snapshot") {
        runCatching { context.uiSnapshotJson() }.fold(
            onSuccess = { call.respondJson(it) },
            onFailure = {
                call.respondError(HttpStatusCode.InternalServerError, it.message ?: "ui_snapshot failed")
            },
        )
    }

    get("/nav_map") {
        val map = context.hooks.navigationMap()
        if (map == null) {
            call.respondError(
                HttpStatusCode.NotFound,
                "this app declared no navigation map. Pass ProbeHooks(navigationMap = { ... }) " +
                    "when calling startProbe, then use /ui_snapshot to find your way around.",
            )
        } else {
            call.respondJson(map.toJson())
        }
    }

    get("/analytics_events") {
        call.respondJson(AnalyticsRecorder.snapshot().toJson())
    }

    post("/analytics_events/clear") {
        AnalyticsRecorder.clear()
        call.respondOk()
    }

    get("/screenshot") {
        runCatching { context.driver.screenshot() }.fold(
            onSuccess = { call.respondBytes(it, ContentType.Image.PNG) },
            onFailure = {
                call.respondError(HttpStatusCode.InternalServerError, it.message ?: "screenshot failed")
            },
        )
    }

    get("/logs") {
        val lines = context.logStream.snapshot().map { JsonPrimitive(it) }
        call.respondJson(buildJsonObject { put("lines", JsonArray(lines)) })
    }

    post("/tap") {
        val args = call.body()
        val x = args.float("x")
        val y = args.float("y")
        if (x == null || y == null) {
            return@post call.respondError(HttpStatusCode.BadRequest, "tap requires numeric x and y")
        }
        runCatching { context.driver.tap(x, y) }.fold(
            onSuccess = { call.respondOk() },
            onFailure = { call.respondError(HttpStatusCode.InternalServerError, it.message ?: "tap failed") },
        )
    }

    post("/swipe") {
        val args = call.body()
        val startX = args.float("startX")
        val startY = args.float("startY")
        val endX = args.float("endX")
        val endY = args.float("endY")
        if (startX == null || startY == null || endX == null || endY == null) {
            return@post call.respondError(
                HttpStatusCode.BadRequest,
                "swipe requires numeric startX, startY, endX and endY",
            )
        }
        runCatching {
            context.driver.swipe(startX, startY, endX, endY, args.long("durationMs") ?: 300L)
        }.fold(
            onSuccess = { call.respondOk() },
            onFailure = { call.respondError(HttpStatusCode.InternalServerError, it.message ?: "swipe failed") },
        )
    }

    post("/input_text") {
        val text = call.body().string("text")
            ?: return@post call.respondError(HttpStatusCode.BadRequest, "input_text requires text")
        runCatching { context.driver.typeText(text) }.fold(
            onSuccess = { call.respondOk() },
            onFailure = {
                call.respondError(HttpStatusCode.InternalServerError, it.message ?: "input_text failed")
            },
        )
    }

    post("/press_back") {
        runCatching { context.driver.pressBack() }.fold(
            onSuccess = { call.respondOk() },
            onFailure = {
                call.respondError(HttpStatusCode.InternalServerError, it.message ?: "press_back failed")
            },
        )
    }
}

/** The `ui_snapshot` payload: where we are, how big the screen is, and everything drawn on it. */
private fun ToolContext.uiSnapshotJson(): JsonObject {
    val elements = elementSource.snapshot()
    val (width, height) = driver.displaySize()
    return buildJsonObject {
        put("breadcrumb", JsonPrimitive(hooks.breadcrumb()))
        put(
            "display",
            buildJsonObject {
                put("width", JsonPrimitive(width))
                put("height", JsonPrimitive(height))
            },
        )
        put(
            "elements",
            JsonArray(
                elements.map {
                    buildJsonObject {
                        put("id", JsonPrimitive(it.id))
                        put("text", JsonPrimitive(it.text))
                        put("role", JsonPrimitive(it.role))
                        put("x", JsonPrimitive(it.x))
                        put("y", JsonPrimitive(it.y))
                        put("width", JsonPrimitive(it.width))
                        put("height", JsonPrimitive(it.height))
                        put("clickable", JsonPrimitive(it.clickable))
                    }
                },
            ),
        )
    }
}
