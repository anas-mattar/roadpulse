package com.roadpulse.app.data

import com.roadpulse.app.util.wallClockMillis
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.abs

/**
 * Roads that drivers reported as closed, so routes are planned around them.
 *
 * Before a route is planned, the active "Road closed" reports around the trip are read from the
 * server; the routing server is then asked to leave out the road nearest to each one. Closures
 * right next to the start, a stop or the destination are ignored (a route cannot leave or reach a
 * road that is left out), and if no route exists around the closures, the route is planned
 * without them (see [RoutingClient]).
 */
object Closures {
    /** The reports API accepts areas up to this size (degrees) per request. */
    const val MAX_BOX_DEG = 0.5
    /** Extra area around the trip, so closures on detours near the direct line are seen too. */
    const val MARGIN_DEG = 0.03
    const val NEAR_END_M = 150.0
    const val MAX_CLOSURES = 20
    private const val FRESH_MS = 120_000L

    private class Fetched(val areas: List<MapArea>, val atMillis: Long, val closures: List<GeoPoint>)
    private var last: Fetched? = null

    /**
     * The areas to ask the server about: one around the whole trip when it fits in a request,
     * otherwise one around the start and one around the destination. Pure: tested.
     */
    fun areas(from: GeoPoint, to: GeoPoint): List<MapArea> {
        val whole = MapArea(
            minOf(from.lon, to.lon) - MARGIN_DEG, minOf(from.lat, to.lat) - MARGIN_DEG,
            maxOf(from.lon, to.lon) + MARGIN_DEG, maxOf(from.lat, to.lat) + MARGIN_DEG,
        )
        if (whole.width <= MAX_BOX_DEG && whole.height <= MAX_BOX_DEG) return listOf(whole)
        val half = MAX_BOX_DEG / 2 - 0.001
        return listOf(from, to).map { MapArea(it.lon - half, it.lat - half, it.lon + half, it.lat + half) }
    }

    /**
     * The closures to plan around: not those within [NEAR_END_M] of the start, a stop or the
     * destination, and at most [MAX_CLOSURES] (the ones nearest the start). Pure: tested.
     */
    fun usable(closures: List<GeoPoint>, from: GeoPoint, ends: List<GeoPoint>): List<GeoPoint> =
        closures.distinct()
            .filter { c -> (ends + from).none { Geo.distanceMeters(it, c) < NEAR_END_M } }
            .sortedBy { Geo.distanceMeters(from, it) }
            .take(MAX_CLOSURES)

    /**
     * Closed roads to plan around for a trip from [from] via [stops] to [to]. Empty without a
     * server, when the server is slow (more than [budgetMs]) or cannot be reached: routing never
     * waits long for this. A recent answer that covers the trip is reused.
     */
    suspend fun around(backendUrl: String, from: GeoPoint, to: GeoPoint, stops: List<GeoPoint> = emptyList(), budgetMs: Long = 1500): List<GeoPoint> {
        if (backendUrl.isEmpty()) return emptyList()
        val now = wallClockMillis()
        val recent = last?.takeIf { f -> abs(now - f.atMillis) < FRESH_MS && listOf(from, to).all { p -> f.areas.any { it.contains(p) } } }
        val closures = recent?.closures ?: withTimeoutOrNull(budgetMs) {
            runCatching {
                val areas = areas(from, to)
                val found = areas.flatMap { area ->
                    Http.json.decodeFromString(ListSerializer(ReportDto.serializer()), Http.execute("$backendUrl/v1/reports?bbox=${area.bboxParam()}"))
                        .filter { it.type == ReportType.ROAD_CLOSED }
                        .map { GeoPoint(it.lat, it.lon) }
                }
                last = Fetched(areas, now, found)
                found
            }.getOrNull()
        } ?: return emptyList()
        return usable(closures, from, stops + to)
    }

    /** Forgets the remembered closures (tests, and when the server changes). */
    fun forget() { last = null }
}

/**
 * The direction the phone was last seen travelling, so a new route starts the way the car is
 * already going. Only trusted while moving, for a few seconds, and near where it was measured.
 */
object TravelHeading {
    private const val MIN_MPS = 3.0
    private const val FRESH_MS = 6_000L
    private const val NEAR_M = 100.0
    private var at: GeoPoint? = null
    private var deg = -1.0
    private var atMillis = 0L

    /** Call on every GPS fix; a negative [bearingDeg] or [speedMps] means "not known" (as on iPhone). */
    fun update(lat: Double, lon: Double, bearingDeg: Double, speedMps: Double) {
        if (bearingDeg >= 0 && speedMps >= MIN_MPS) {
            at = GeoPoint(lat, lon); deg = bearingDeg; atMillis = wallClockMillis()
        } else {
            deg = -1.0
        }
    }

    /** The heading to start a route from [from] with, or null when it is not known well enough. */
    fun from(from: GeoPoint, nowMillis: Long = wallClockMillis()): Double? {
        val p = at ?: return null
        return deg.takeIf { it >= 0 && abs(nowMillis - atMillis) <= FRESH_MS && Geo.distanceMeters(p, from) <= NEAR_M }
    }

    fun forget() { at = null; deg = -1.0 }
}
