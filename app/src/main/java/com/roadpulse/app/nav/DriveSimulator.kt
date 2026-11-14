package com.roadpulse.app.nav

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Generates GPS fixes for a car driving along a route line at constant speed, with Gaussian
 * position and heading noise. Pure Kotlin: used by [DriveReplay] on the device and by unit tests.
 */
class DriveSimulator(
    private val shape: List<GeoPoint>,
    private val speedMps: Double,
    private val noiseM: Double = 8.0,
    seed: Long = 42L,
) {
    private val cumulative = DoubleArray(shape.size).also { c ->
        for (i in 1 until shape.size) c[i] = c[i - 1] + Geo.distanceMeters(shape[i - 1], shape[i])
    }
    val totalMeters: Double = cumulative.lastOrNull() ?: 0.0
    private val random = Random(seed)

    var alongM = 0.0
        private set
    val finished: Boolean get() = alongM >= totalMeters

    /** Exact (noise-free) position at the current progress. */
    fun position(): GeoPoint = pointAt(alongM)
    fun bearing(): Double = bearingAt(alongM)

    /** Advances by [stepSeconds] of driving and returns the (noisy) fix at the new position. */
    fun next(timeMillis: Long, stepSeconds: Double = 1.0): LocationFix {
        alongM = min(totalMeters, alongM + speedMps * stepSeconds)
        return noisyFix(pointAt(alongM), bearingAt(alongM), if (finished) 0.0 else speedMps, timeMillis)
    }

    fun noisyFix(p: GeoPoint, bearing: Double, speed: Double, timeMillis: Long): LocationFix {
        val noisy = offset(offset(p, 0.0, random.nextGaussian() * noiseM), 90.0, random.nextGaussian() * noiseM)
        return LocationFix(
            position = noisy,
            accuracyM = noiseM.coerceAtLeast(3.0),
            bearingDeg = if (speed > 0) (bearing + random.nextGaussian() * 4 + 360) % 360 else null,
            speedMps = speed,
            timeMillis = timeMillis,
        )
    }

    private fun segmentAt(d: Double): Int {
        if (shape.size < 2) return 0
        var i = 0
        while (i < shape.size - 2 && cumulative[i + 1] < d) i++
        return i
    }

    private fun pointAt(d: Double): GeoPoint {
        if (shape.size < 2) return shape.first()
        val i = segmentAt(d)
        val len = cumulative[i + 1] - cumulative[i]
        val t = if (len <= 0) 0.0 else ((d - cumulative[i]) / len).coerceIn(0.0, 1.0)
        val a = shape[i]
        val b = shape[i + 1]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
    }

    private fun bearingAt(d: Double): Double {
        if (shape.size < 2) return 0.0
        val i = segmentAt(d)
        val a = shape[i]
        val b = shape[i + 1]
        val k = cos(Math.toRadians(a.lat))
        return (Math.toDegrees(atan2((b.lon - a.lon) * k, b.lat - a.lat)) + 360) % 360
    }

    companion object {
        private const val M_PER_DEG = 111_195.0

        /** Point [meters] away from [p] in direction [bearingDeg] (flat-earth, fine for < 10 km). */
        fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
            val r = Math.toRadians(bearingDeg)
            val dLat = meters * cos(r) / M_PER_DEG
            val dLon = meters * sin(r) / (M_PER_DEG * cos(Math.toRadians(p.lat)))
            return GeoPoint(p.lat + dLat, p.lon + dLon)
        }
    }
}
