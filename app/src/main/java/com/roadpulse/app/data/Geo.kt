package com.roadpulse.app.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Index of the route vertex closest to [position]. */
    fun nearestIndex(shape: List<GeoPoint>, position: GeoPoint): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        shape.forEachIndexed { i, p ->
            val d = distanceMeters(p, position)
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }
}
