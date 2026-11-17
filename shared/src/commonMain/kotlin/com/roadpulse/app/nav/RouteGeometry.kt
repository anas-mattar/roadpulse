package com.roadpulse.app.nav

import com.roadpulse.app.util.toRadians

import com.roadpulse.app.data.GeoPoint
import kotlin.math.cos

/** Small geometry helpers on a route line. Pure Kotlin: unit tested. */
object RouteGeometry {
    /** The part of [shape] still ahead of [position] (the whole line when the position is unknown). */
    fun remaining(shape: List<GeoPoint>, position: GeoPoint?): List<GeoPoint> {
        if (position == null || shape.size < 2) return shape
        val k = cos(toRadians(position.lat))
        val nearest = shape.indices.minBy { i ->
            val dx = (shape[i].lon - position.lon) * k
            val dy = shape[i].lat - position.lat
            dx * dx + dy * dy
        }
        return listOf(position) + shape.drop(nearest + 1)
    }

    /**
     * The last [remainingMeters] of [shape]: the route still ahead of the car, starting exactly
     * where the car is on the line (so the part already driven is not drawn).
     */
    fun ahead(shape: List<GeoPoint>, remainingMeters: Double): List<GeoPoint> {
        if (shape.size < 2) return shape
        var left = remainingMeters
        if (left <= 0) return listOf(shape.last())
        for (i in shape.lastIndex downTo 1) {
            val segment = com.roadpulse.app.data.Geo.distanceMeters(shape[i - 1], shape[i])
            if (segment >= left) {
                val back = if (segment > 0) left / segment else 0.0
                val start = GeoPoint(
                    shape[i].lat + (shape[i - 1].lat - shape[i].lat) * back,
                    shape[i].lon + (shape[i - 1].lon - shape[i].lon) * back,
                )
                return listOf(start) + shape.subList(i, shape.size)
            }
            left -= segment
        }
        return shape
    }
}
