package com.roadpulse.app.nav

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/** One raw position reading from the location provider. */
data class LocationFix(
    val position: GeoPoint,
    /** Horizontal accuracy radius in metres (68% confidence), if the provider reports it. */
    val accuracyM: Double? = null,
    /** Direction of travel in degrees clockwise from north, if reported. */
    val bearingDeg: Double? = null,
    val speedMps: Double? = null,
    /** Monotonic timestamp (e.g. elapsedRealtime) in milliseconds. */
    val timeMillis: Long,
)

/** Result of matching one fix against the route line. */
data class Match(
    /** Closest point on the route: the position to display while on route. */
    val snapped: GeoPoint,
    val segmentIndex: Int,
    /** Metres from the route start to [snapped]. */
    val distanceAlongM: Double,
    /** Metres between the raw fix and [snapped]. */
    val offsetM: Double,
    /** Angle between travel direction and the route segment; null when heading is unreliable. */
    val headingDiffDeg: Double?,
    /** This fix on its own is consistent with driving on the route (distance and direction). */
    val onRoute: Boolean,
)

/**
 * Matches GPS fixes to the active route.
 *
 * - Measures distance to route *segments*, not vertices. Routing shapes can have vertices
 *   more than 1 km apart on straight highways.
 * - Searches only a window around the last matched progress, so a route that passes near
 *   itself (out-and-back roads, loops, overpasses) cannot make progress jump.
 * - Uses travel direction when moving fast enough to trust it, so the opposite carriageway or a
 *   U-turn does not count as being on route.
 *
 * Pure Kotlin so it can be unit tested without Android.
 */
class RouteMatcher(
    private val shape: List<GeoPoint>,
    /** Where the driver is expected to start; null = anywhere (search the whole route first). */
    startAlongM: Double? = 0.0,
) {

    val cumulative: DoubleArray = DoubleArray(shape.size).also { c ->
        for (i in 1 until shape.size) c[i] = c[i - 1] + Geo.distanceMeters(shape[i - 1], shape[i])
    }
    val totalMeters: Double = cumulative.lastOrNull() ?: 0.0

    private val segmentBearing = DoubleArray((shape.size - 1).coerceAtLeast(0)) { i ->
        bearing(shape[i], shape[i + 1])
    }

    /** Progress of the last fix that matched the route; null until known (resumed drive). */
    private var anchorAlongM: Double? = startAlongM
    private var anchorTimeMillis: Long? = null

    /**
     * @param commit when false (e.g. a low-accuracy fix) the match is computed but does not move
     *               the search window.
     */
    fun match(fix: LocationFix, commit: Boolean = true): Match {
        if (shape.size < 2) {
            val only = shape.firstOrNull() ?: fix.position
            val d = Geo.distanceMeters(only, fix.position)
            return Match(only, 0, 0.0, d, null, d <= tolerance(fix))
        }

        val anchorTime = anchorTimeMillis ?: fix.timeMillis.also { anchorTimeMillis = it }
        val dtSeconds = ((fix.timeMillis - anchorTime) / 1000.0).coerceAtLeast(0.0)
        val anchor = anchorAlongM
        val windowStart = if (anchor == null) Double.NEGATIVE_INFINITY else anchor - WINDOW_BACK_M
        val windowEnd = if (anchor == null) Double.POSITIVE_INFINITY
        else anchor + WINDOW_AHEAD_BASE_M + MAX_PLAUSIBLE_SPEED_MPS * dtSeconds

        val headingReliable = fix.bearingDeg != null && (fix.speedMps ?: 0.0) >= MIN_HEADING_SPEED_MPS

        var best: Candidate? = null
        for (i in 0 until shape.size - 1) {
            if (cumulative[i + 1] < windowStart || cumulative[i] > windowEnd) continue
            val c = project(fix.position, i)
            val diff = if (headingReliable) angleDiff(fix.bearingDeg!!, segmentBearing[i]) else null
            val score = c.offsetM + (diff?.let { it / 180.0 * HEADING_WEIGHT_M } ?: 0.0)
            if (best == null || score < best.score) best = c.copy(headingDiffDeg = diff, score = score)
        }
        val b = best ?: project(fix.position, 0) // window cannot be empty, but stay safe

        val onRoute = b.offsetM <= tolerance(fix) &&
            (b.headingDiffDeg == null || b.headingDiffDeg <= MAX_HEADING_DIFF_DEG)
        if (onRoute && commit) {
            anchorAlongM = b.alongM
            anchorTimeMillis = fix.timeMillis
        }
        return Match(b.snapped, b.segmentIndex, b.alongM, b.offsetM, b.headingDiffDeg, onRoute)
    }

    /** Distance from the route that is still treated as on route for this fix. */
    fun tolerance(fix: LocationFix): Double =
        BASE_TOLERANCE_M + min(fix.accuracyM ?: DEFAULT_ACCURACY_M, MAX_ACCURACY_ALLOWANCE_M)

    private data class Candidate(
        val segmentIndex: Int,
        val snapped: GeoPoint,
        val alongM: Double,
        val offsetM: Double,
        val headingDiffDeg: Double? = null,
        val score: Double = 0.0,
    )

    /** Projects [p] onto segment i using a local flat-earth approximation centred on p. */
    private fun project(p: GeoPoint, i: Int): Candidate {
        val a = shape[i]
        val b = shape[i + 1]
        val k = cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * k * M_PER_DEG
        val ay = (a.lat - p.lat) * M_PER_DEG
        val dx = (b.lon - a.lon) * k * M_PER_DEG
        val dy = (b.lat - a.lat) * M_PER_DEG
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val px = ax + t * dx
        val py = ay + t * dy
        val snapped = GeoPoint(p.lat + py / M_PER_DEG, p.lon + px / (k * M_PER_DEG))
        val along = cumulative[i] + t * (cumulative[i + 1] - cumulative[i])
        return Candidate(i, snapped, along, sqrt(px * px + py * py))
    }

    companion object {
        private const val M_PER_DEG = 111_195.0
        const val BASE_TOLERANCE_M = 30.0
        const val DEFAULT_ACCURACY_M = 10.0
        const val MAX_ACCURACY_ALLOWANCE_M = 40.0
        const val WINDOW_BACK_M = 50.0
        const val WINDOW_AHEAD_BASE_M = 500.0
        const val MAX_PLAUSIBLE_SPEED_MPS = 45.0 // ~160 km/h
        const val MIN_HEADING_SPEED_MPS = 3.0    // below ~11 km/h GPS bearing is noise
        const val MAX_HEADING_DIFF_DEG = 100.0
        const val HEADING_WEIGHT_M = 60.0

        fun angleDiff(a: Double, b: Double): Double = abs(((a - b) % 360 + 540) % 360 - 180)

        private fun bearing(a: GeoPoint, b: GeoPoint): Double {
            val k = cos(Math.toRadians((a.lat + b.lat) / 2))
            val dx = (b.lon - a.lon) * k
            val dy = b.lat - a.lat
            return (Math.toDegrees(atan2(dx, dy)) + 360) % 360
        }
    }
}

/**
 * Declares "off route" only after the driver has been away from the route for several fixes
 * AND several seconds, so one bad GPS reading cannot trigger a reroute.
 */
class OffRouteDetector(
    private val minBadFixes: Int = 3,
    private val minBadMillis: Long = 4_000,
) {
    private var badCount = 0
    private var badSinceMillis: Long? = null
    var offRoute = false
        private set

    fun onFix(onRoute: Boolean, timeMillis: Long): Boolean {
        if (onRoute) {
            badCount = 0
            badSinceMillis = null
            offRoute = false
        } else {
            val since = badSinceMillis ?: timeMillis.also { badSinceMillis = it }
            badCount++
            if (badCount >= minBadFixes && timeMillis - since >= minBadMillis) offRoute = true
        }
        return offRoute
    }
}
