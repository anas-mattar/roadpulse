package com.roadpulse.app.nav

import com.roadpulse.app.util.toRadians

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.TrafficLine
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * How much live traffic slows a route down, and which areas a detour should avoid.
 * The routing server plans with normal speeds; this adds the delay from roads that RoadPulse
 * drivers measured as slow or jammed. Pure Kotlin: unit tested.
 */
object TrafficDelay {
    /** A traffic line counts as on the route when most of its points are this close to it. */
    const val ON_ROUTE_M = 25.0

    /** Extra seconds on [route] (the part still ahead) caused by [lines], and the lines involved. */
    fun estimate(route: List<GeoPoint>, lines: List<TrafficLine>): Pair<Double, List<TrafficLine>> {
        if (route.size < 2) return 0.0 to emptyList()
        var seconds = 0.0
        val hit = mutableListOf<TrafficLine>()
        for (line in lines) {
            val pts = line.points.map { GeoPoint(it[0], it[1]) }
            if (pts.size < 2 || line.speedKph <= 0) continue
            val near = pts.count { distanceToLine(it, route) <= ON_ROUTE_M }
            if (near * 2 < pts.size + 1) continue // not along this route (e.g. a crossing road)
            val lengthM = pts.zipWithNext { a, b -> metres(a, b) }.sum()
            val slow = lengthM / (line.speedKph / 3.6)
            val normal = lengthM / (maxOf(line.expectedKph, line.speedKph) / 3.6)
            seconds += (slow - normal).coerceAtLeast(0.0)
            hit += line
        }
        return seconds to hit
    }

    /** Small boxes around jammed lines, for the routing server to avoid (at most [max]). */
    fun avoidAreas(lines: List<TrafficLine>, max: Int = 10, marginDeg: Double = 0.0003): List<List<GeoPoint>> =
        lines.filter { it.level == "JAM" }.take(max).map { line ->
            val lats = line.points.map { it[0] }
            val lons = line.points.map { it[1] }
            val s = lats.min() - marginDeg
            val n = lats.max() + marginDeg
            val w = lons.min() - marginDeg
            val e = lons.max() + marginDeg
            listOf(GeoPoint(s, w), GeoPoint(s, e), GeoPoint(n, e), GeoPoint(n, w), GeoPoint(s, w))
        }

    /** The route ahead up to [maxM] metres, small enough for one traffic request. */
    fun ahead(shape: List<GeoPoint>, from: GeoPoint?, maxM: Double = 30_000.0): List<GeoPoint> {
        val rest = RouteGeometry.remaining(shape, from)
        val out = mutableListOf<GeoPoint>()
        var total = 0.0
        for (p in rest) {
            out.lastOrNull()?.let { total += metres(it, p) }
            out += p
            if (total >= maxM) break
        }
        return out
    }

    fun metres(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(toRadians(a.lat))
        val dx = (b.lon - a.lon) * k * 111_320
        val dy = (b.lat - a.lat) * 110_540
        return sqrt(dx * dx + dy * dy)
    }

    private fun distanceToLine(p: GeoPoint, line: List<GeoPoint>): Double {
        val k = cos(toRadians(p.lat))
        var best = Double.MAX_VALUE
        for (i in 0 until line.size - 1) {
            val ax = (line[i].lon - p.lon) * k * 111_320
            val ay = (line[i].lat - p.lat) * 110_540
            val dx = (line[i + 1].lon - line[i].lon) * k * 111_320
            val dy = (line[i + 1].lat - line[i].lat) * 110_540
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val x = ax + t * dx
            val y = ay + t * dy
            best = minOf(best, sqrt(x * x + y * y))
        }
        return best
    }
}
