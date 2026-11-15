package com.roadpulse.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Route calculation backed by a Valhalla server (OpenStreetMap data). */
class RoutingClient(private val baseUrl: String) {

    fun requestBody(
        from: GeoPoint,
        to: GeoPoint,
        stops: List<GeoPoint> = emptyList(),
        options: RouteOptions = RouteOptions(),
        alternates: Int? = null,
        avoid: List<List<GeoPoint>> = emptyList(),
    ): String = Http.json.encodeToString(
        RouteRequest(
            locations = (listOf(from) + stops + to).map { Location(it.lat, it.lon) },
            costing = options.vehicle.costing,
            costingOptions = options.costingOptions(),
            // The server offers alternatives only for trips without stops.
            alternates = if (stops.isEmpty()) alternates else null,
            excludePolygons = avoid.takeIf { it.isNotEmpty() }?.map { ring -> ring.map { listOf(it.lon, it.lat) } },
        )
    )

    /** The fastest route plus up to [alternates] alternatives (first element = fastest). */
    suspend fun routes(
        from: GeoPoint,
        to: GeoPoint,
        alternates: Int = 2,
        stops: List<GeoPoint> = emptyList(),
        options: RouteOptions = RouteOptions(),
    ): List<Route> {
        val payload = requestBody(from, to, stops, options, alternates)
        return parseRoutes(Http.execute("$baseUrl/route", "POST", payload, Http.USER_AGENT), stops)
    }

    /** Parses the main route and any alternates (visible for tests). */
    fun parseRoutes(body: String, stops: List<GeoPoint> = emptyList()): List<Route> {
        val response = Http.json.decodeFromString<RouteResponse>(body)
        return listOf(toRoute(response.trip, stops)) + response.alternates.map { toRoute(it.trip, stops) }
    }

    /**
     * Speed limits along [route], matched edge by edge to the route's own shape points.
     * Returns [SpeedLimits.NONE] when the server has no data; throws on network errors.
     */
    suspend fun speedLimits(route: Route): SpeedLimits {
        if (route.shape.size < 2) return SpeedLimits.NONE
        val payload = Http.json.encodeToString(TraceRequest(encodedPolyline = Polyline.encode(route.shape)))
        return parseSpeedLimits(Http.execute("$baseUrl/trace_attributes", "POST", payload, Http.USER_AGENT))
    }

    /** Parses a trace_attributes response (visible for tests). */
    fun parseSpeedLimits(body: String): SpeedLimits {
        val edges = Http.json.decodeFromString<TraceResponse>(body).edges
        return SpeedLimits(
            edges.mapNotNull { e ->
                val kph = e.speedLimit?.let { (it as? JsonPrimitive)?.intOrNull } ?: return@mapNotNull null
                val begin = e.beginShapeIndex ?: return@mapNotNull null
                val end = e.endShapeIndex ?: return@mapNotNull null
                if (kph <= 0 || end <= begin) null else SpeedLimitSpan(begin, end, kph)
            }
        )
    }

    /** Single fastest route (used for rerouting, where speed matters more than choice). */
    suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        stops: List<GeoPoint> = emptyList(),
        options: RouteOptions = RouteOptions(),
        avoid: List<List<GeoPoint>> = emptyList(),
    ): Route {
        val payload = requestBody(from, to, stops, options, avoid = avoid)
        return parseRoute(Http.execute("$baseUrl/route", "POST", payload, Http.USER_AGENT), stops)
    }

    /** Parses a routing-server response body (visible for tests). */
    fun parseRoute(body: String, stops: List<GeoPoint> = emptyList()): Route =
        toRoute(Http.json.decodeFromString<RouteResponse>(body).trip, stops)

    /**
     * Joins the trip's legs (one per stop) into one route: shapes are chained, maneuver shape
     * indexes shifted, and the arrival at each intermediate stop becomes [STOP_REACHED].
     */
    private fun toRoute(trip: Trip, stops: List<GeoPoint>): Route {
        if (trip.legs.isEmpty()) error("Routing server returned no route")
        val shape = ArrayList<GeoPoint>()
        val maneuvers = ArrayList<Maneuver>()
        val stopIndexes = ArrayList<Int>()
        trip.legs.forEachIndexed { legIndex, leg ->
            val legShape = Polyline.decode(leg.shape, precision = 6)
            // Each leg starts where the previous one ended: drop the repeated point.
            val offset = if (shape.isEmpty()) 0 else shape.size - 1
            shape.addAll(if (shape.isEmpty()) legShape else legShape.drop(1))
            val last = legIndex == trip.legs.lastIndex
            legManeuvers(leg).forEachIndexed { i, m ->
                val shifted = m.copy(beginShapeIndex = m.beginShapeIndex + offset)
                val isLegEnd = i == leg.maneuvers.lastIndex
                when {
                    isLegEnd && !last -> {
                        stopIndexes += shifted.beginShapeIndex
                        maneuvers += shifted.copy(
                            type = STOP_REACHED,
                            instruction = "You have reached your stop.",
                            verbalAlert = "Arrive at your stop.",
                            verbalPre = "You have reached your stop.",
                        )
                    }
                    // A leg after a stop begins with "Drive …": keep it, the driver sets off again.
                    else -> maneuvers += shifted
                }
            }
        }
        return Route(
            shape = shape,
            maneuvers = withRoundaboutNames(maneuvers),
            lengthKm = trip.summary.length,
            timeSeconds = trip.summary.time,
            hasToll = trip.summary.hasToll,
            hasHighway = trip.summary.hasHighway,
            stops = if (stopIndexes.size == stops.size) stops else emptyList(),
            stopShapeIndexes = if (stopIndexes.size == stops.size) stopIndexes else emptyList(),
        )
    }

    private fun legManeuvers(leg: Leg): List<Maneuver> = leg.maneuvers.map { m ->
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
                    lanes = m.lanes.map { Lane(it.directions, it.valid ?: 0, it.active ?: 0) },
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

    companion object {
        private const val ROUNDABOUT_ENTER = 26
        private const val ROUNDABOUT_EXIT = 27
        /** RoadPulse's own maneuver type: arriving at a stop on the way (not a Valhalla code). */
        const val STOP_REACHED = ManeuverTypes.STOP_REACHED
    }

    @Serializable
    private data class TraceRequest(
        @SerialName("encoded_polyline") val encodedPolyline: String,
        @SerialName("shape_match") val shapeMatch: String = "edge_walk",
        val costing: String = "auto",
        val units: String = "kilometers",
        val filters: TraceFilters = TraceFilters(),
    )

    @Serializable
    private data class TraceFilters(
        val attributes: List<String> = listOf("edge.speed_limit", "edge.begin_shape_index", "edge.end_shape_index"),
        val action: String = "include",
    )

    @Serializable
    private data class TraceResponse(val edges: List<TraceEdge> = emptyList())

    @Serializable
    private data class TraceEdge(
        /** A number in km/h, or the string "unlimited", or absent when unknown. */
        @SerialName("speed_limit") val speedLimit: JsonElement? = null,
        @SerialName("begin_shape_index") val beginShapeIndex: Int? = null,
        @SerialName("end_shape_index") val endShapeIndex: Int? = null,
    )

    @Serializable
    private data class RouteRequest(
        val locations: List<Location>,
        val costing: String = "auto",
        @SerialName("costing_options") val costingOptions: kotlinx.serialization.json.JsonObject? = null,
        val units: String = "kilometers",
        val language: String = "en-US",
        /** Extra alternative routes to return (0 = only the fastest). Omitted from JSON when 0. */
        val alternates: Int? = null,
        /** Ask for lane arrows on each maneuver ("turn_lanes"). Servers that do not know it ignore it. */
        @SerialName("turn_lanes") val turnLanes: Boolean = true,
        /** Areas to avoid, each a closed ring of [lon, lat] pairs (jammed roads). Omitted when null. */
        @SerialName("exclude_polygons") val excludePolygons: List<List<List<Double>>>? = null,
    )

    @Serializable
    private data class Location(val lat: Double, val lon: Double)

    @Serializable
    private data class RouteResponse(val trip: Trip, val alternates: List<Alternate> = emptyList())

    @Serializable
    private data class Alternate(val trip: Trip)

    @Serializable
    private data class Trip(val legs: List<Leg> = emptyList(), val summary: Summary = Summary())

    @Serializable
    private data class Summary(
        val length: Double = 0.0,
        val time: Double = 0.0,
        @SerialName("has_toll") val hasToll: Boolean = false,
        @SerialName("has_highway") val hasHighway: Boolean = false,
    )

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
        val lanes: List<LaneDto> = emptyList(),
    )

    @Serializable
    private data class LaneDto(val directions: Int = 0, val valid: Int? = null, val active: Int? = null)

    @Serializable
    private data class SignDto(
        @SerialName("exit_number_elements") val exitNumber: List<SignElement> = emptyList(),
        @SerialName("exit_branch_elements") val exitBranch: List<SignElement> = emptyList(),
        @SerialName("exit_toward_elements") val exitToward: List<SignElement> = emptyList(),
    )

    @Serializable
    private data class SignElement(val text: String = "")
}
