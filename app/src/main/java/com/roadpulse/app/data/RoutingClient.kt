package com.roadpulse.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Route calculation backed by a Valhalla server (OpenStreetMap data). */
class RoutingClient(private val baseUrl: String) {

    internal fun requestBody(from: GeoPoint, to: GeoPoint): String = Http.json.encodeToString(
        RouteRequest(locations = listOf(Location(from.lat, from.lon), Location(to.lat, to.lon)))
    )

    suspend fun route(from: GeoPoint, to: GeoPoint): Route {
        val payload = requestBody(from, to)
        val request = Request.Builder()
            .url("$baseUrl/route")
            .header("User-Agent", "RoadPulse/0.1")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return parseRoute(Http.execute(request))
    }

    /** Parses a routing-server response body (visible for tests). */
    internal fun parseRoute(body: String): Route {
        val trip = Http.json.decodeFromString<RouteResponse>(body).trip
        val leg = trip.legs.firstOrNull() ?: error("Routing server returned no route")
        return Route(
            shape = Polyline.decode(leg.shape, precision = 6),
            maneuvers = withRoundaboutNames(leg.maneuvers.map { m ->
                Maneuver(
                    instruction = m.instruction,
                    lengthKm = m.length,
                    timeSeconds = m.time,
                    beginShapeIndex = m.beginShapeIndex,
                    verbalAlert = m.verbalAlert,
                    verbalPre = m.verbalPre,
                    type = m.type,
                    streetNames = m.streetNames.map(String::trim).filter(String::isNotEmpty),
                    bearingAfter = m.bearingAfter,
                    roundaboutExitCount = m.roundaboutExitCount,
                    exitNumber = m.sign?.exitNumber?.firstOrNull()?.text?.trim()?.ifEmpty { null },
                    exitBranch = m.sign?.exitBranch.orEmpty().map { it.text.trim() }.filter(String::isNotEmpty),
                    exitToward = m.sign?.exitToward.orEmpty().map { it.text.trim() }.filter(String::isNotEmpty),
                )
            }),
            lengthKm = trip.summary.length,
            timeSeconds = trip.summary.time,
        )
    }

    /**
     * The server names the road on the "exit roundabout" step, not on "enter roundabout";
     * copy it back so "take the 2nd exit onto …" can be said in any language.
     */
    private fun withRoundaboutNames(maneuvers: List<Maneuver>): List<Maneuver> =
        maneuvers.mapIndexed { i, m ->
            val next = maneuvers.getOrNull(i + 1)
            if (m.type == ROUNDABOUT_ENTER && m.streetNames.isEmpty() && next?.type == ROUNDABOUT_EXIT) {
                m.copy(streetNames = next.streetNames)
            } else {
                m
            }
        }

    private companion object {
        const val ROUNDABOUT_ENTER = 26
        const val ROUNDABOUT_EXIT = 27
    }

    @Serializable
    private data class RouteRequest(
        val locations: List<Location>,
        val costing: String = "auto",
        val units: String = "kilometers",
        val language: String = "en-US",
    )

    @Serializable
    private data class Location(val lat: Double, val lon: Double)

    @Serializable
    private data class RouteResponse(val trip: Trip)

    @Serializable
    private data class Trip(val legs: List<Leg> = emptyList(), val summary: Summary = Summary())

    @Serializable
    private data class Summary(val length: Double = 0.0, val time: Double = 0.0)

    @Serializable
    private data class Leg(val shape: String, val maneuvers: List<ManeuverDto> = emptyList())

    @Serializable
    private data class ManeuverDto(
        val instruction: String = "",
        val length: Double = 0.0,
        val time: Double = 0.0,
        @SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
        @SerialName("verbal_transition_alert_instruction") val verbalAlert: String? = null,
        @SerialName("verbal_pre_transition_instruction") val verbalPre: String? = null,
        val type: Int = 0,
        @SerialName("street_names") val streetNames: List<String> = emptyList(),
        @SerialName("bearing_after") val bearingAfter: Int? = null,
        @SerialName("roundabout_exit_count") val roundaboutExitCount: Int? = null,
        val sign: SignDto? = null,
    )

    @Serializable
    private data class SignDto(
        @SerialName("exit_number_elements") val exitNumber: List<SignElement> = emptyList(),
        @SerialName("exit_branch_elements") val exitBranch: List<SignElement> = emptyList(),
        @SerialName("exit_toward_elements") val exitToward: List<SignElement> = emptyList(),
    )

    @Serializable
    private data class SignElement(val text: String = "")
}
