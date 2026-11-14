package com.roadpulse.app.nav

import com.roadpulse.app.data.GeoPoint
import kotlin.math.cos

/** Small geometry helpers on a route line. Pure Kotlin: unit tested. */
object RouteGeometry {
    /** The part of [shape] still ahead of [position] (the whole line when the position is unknown). */
    fun remaining(shape: List<GeoPoint>, position: GeoPoint?): List<GeoPoint> {
        if (position == null || shape.size < 2) return shape
        val k = cos(Math.toRadians(position.lat))
        val nearest = shape.indices.minBy { i ->
            val dx = (shape[i].lon - position.lon) * k
            val dy = shape[i].lat - position.lat
            dx * dx + dy * dy
        }
        return listOf(position) + shape.drop(nearest + 1)
    }
}
