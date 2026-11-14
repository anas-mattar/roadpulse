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
        val trip = Http.json.decodeFromString<RouteResponse>(Http.execute(request)).trip
        val leg = trip.legs.firstOrNull() ?: error("Routing server returned no route")
        return Route(
            shape = Polyline.decode(leg.shape, precision = 6),
            maneuvers = leg.maneuvers.map {
                Maneuver(it.instruction, it.length, it.time, it.beginShapeIndex, it.verbalAlert, it.verbalPre)
            },
            lengthKm = trip.summary.length,
            timeSeconds = trip.summary.time,
        )
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
    )
}
