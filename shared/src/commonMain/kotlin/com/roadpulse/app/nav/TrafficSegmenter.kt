package com.roadpulse.app.nav

import com.roadpulse.app.util.toRadians
import com.roadpulse.app.util.mathRound

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.TrafficPoint
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Cuts a drive into anonymous traffic segments as ADR 0004 requires. Pure Kotlin: unit tested.
 * - Nothing from the first [trimM] metres driven or the last [trimM] metres before the destination,
 *   so home and work are not revealed.
 * - Segments of at most [maxSeconds]; each is sent on its own with no id linking it to the next.
 * - One point every [stepSeconds] at most; times are seconds from the segment start (no clock time).
 */
class TrafficSegmenter(
    private val trimM: Double = 500.0,
    private val maxSeconds: Int = 120,
    private val stepSeconds: Int = 2,
    private val minPoints: Int = 5,
) {
    private var odometerM = 0.0
    private var last: GeoPoint? = null
    private var startMillis: Long? = null
    private var lastPointMillis: Long? = null
    private val buffer = mutableListOf<TrafficPoint>()

    /**
     * Adds a GPS fix. [remainingM] is the distance left to the destination along the route.
     * Returns a finished segment to upload, or null.
     */
    fun add(position: GeoPoint, speedMps: Double?, timeMillis: Long, remainingM: Double): List<TrafficPoint>? {
        last?.let { odometerM += metres(it, position) }
        last = position
        if (remainingM < trimM) { // near the destination: drop what is pending, send nothing more
            buffer.clear()
            startMillis = null
            return null
        }
        if (odometerM < trimM) return null
        lastPointMillis?.let { if (timeMillis - it < stepSeconds * 1000L) return null }
        lastPointMillis = timeMillis
        val start = startMillis ?: timeMillis.also { startMillis = it }
        val t = ((timeMillis - start) / 1000).toInt()
        buffer += TrafficPoint(round5(position.lat), round5(position.lon), t, speedMps?.let { mathRound(it * 10) / 10.0 })
        if (t < maxSeconds) return null
        val done = buffer.toList()
        buffer.clear()
        startMillis = null
        return done.takeIf { it.size >= minPoints }
    }

    /** End of the drive: whatever is pending is within the last stretch, so it is dropped. */
    fun reset() {
        buffer.clear()
        startMillis = null
        odometerM = 0.0
        last = null
        lastPointMillis = null
    }

    private fun round5(v: Double) = mathRound(v * 100_000) / 100_000.0

    private fun metres(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(toRadians(a.lat))
        val dx = (b.lon - a.lon) * k * 111_320
        val dy = (b.lat - a.lat) * 110_540
        return sqrt(dx * dx + dy * dy)
    }
}
