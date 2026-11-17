package com.roadpulse.app.nav

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.TrafficClient
import com.roadpulse.app.data.TrafficLine
import kotlin.coroutines.cancellation.CancellationException

/** A slow (SLOW) or jammed (JAM) stretch of a route, as fractions 0..1 of its length. */
data class TrafficPiece(val from: Double, val to: Double, val level: String)

/** Live traffic on one route choice: the coloured stretches and the extra time they cost. */
data class RouteTrafficInfo(val pieces: List<TrafficPiece>, val delaySeconds: Double)

/**
 * Where a route is slow or jammed, for the traffic bar on route cards and the colours on the
 * route line. Pure Kotlin (except [forRoutes]): unit tested.
 */
object RouteTraffic {
    /** Stretches of [shape] that run along slow or jammed [lines] (JAM wins over SLOW). */
    fun pieces(shape: List<GeoPoint>, lines: List<TrafficLine>): List<TrafficPiece> {
        if (shape.size < 2) return emptyList()
        val onRoute = TrafficDelay.estimate(shape, lines.filter { it.level != "FREE" }).second
            .map { it.level to it.points.map { p -> GeoPoint(p[0], p[1]) } }
        if (onRoute.isEmpty()) return emptyList()
        val cumulative = DoubleArray(shape.size)
        for (i in 1 until shape.size) cumulative[i] = cumulative[i - 1] + TrafficDelay.metres(shape[i - 1], shape[i])
        val total = cumulative.last()
        if (total <= 0) return emptyList()
        val out = mutableListOf<TrafficPiece>()
        for (i in 0 until shape.size - 1) {
            val a = shape[i]
            val b = shape[i + 1]
            val mid = GeoPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
            var level: String? = null
            for ((lineLevel, pts) in onRoute) {
                if (level == "JAM") break
                if (lineLevel == level) continue
                if (TrafficDelay.distanceToLine(mid, pts) <= TrafficDelay.ON_ROUTE_M) level = lineLevel
            }
            if (level == null) continue
            val from = cumulative[i] / total
            val to = cumulative[i + 1] / total
            val last = out.lastOrNull()
            // Join with the previous stretch when it is the same level and (almost) touching.
            if (last != null && last.level == level && from - last.to < 0.002) out[out.size - 1] = last.copy(to = to)
            else out += TrafficPiece(from, to, level)
        }
        return out
    }

    /** The coloured stretches as lines on the map: (level, points). */
    /** The stretches not yet passed at [progress] (0..1 of the route); one being driven is cut at the car. */
    fun ahead(pieces: List<TrafficPiece>, progress: Double): List<TrafficPiece> =
        pieces.filter { it.to > progress }.map { if (it.from < progress) it.copy(from = progress) else it }

    fun polylines(shape: List<GeoPoint>, pieces: List<TrafficPiece>): List<Pair<String, List<GeoPoint>>> {
        if (shape.size < 2 || pieces.isEmpty()) return emptyList()
        val cumulative = DoubleArray(shape.size)
        for (i in 1 until shape.size) cumulative[i] = cumulative[i - 1] + TrafficDelay.metres(shape[i - 1], shape[i])
        val total = cumulative.last().takeIf { it > 0 } ?: return emptyList()
        return pieces.mapNotNull { piece ->
            val pts = shape.indices.filter { cumulative[it] / total >= piece.from - 1e-9 && cumulative[it] / total <= piece.to + 1e-9 }
                .map { shape[it] }
            if (pts.size >= 2) piece.level to pts else null
        }
    }

    /**
     * Traffic on each route choice, from one traffic request for the area they cover. Empty
     * results without a server, for very large areas, or when the request fails.
     */
    suspend fun forRoutes(backendUrl: String, routes: List<Route>): List<RouteTrafficInfo> {
        val none = routes.map { RouteTrafficInfo(emptyList(), 0.0) }
        val all = routes.flatMap { it.shape }
        if (backendUrl.isEmpty() || all.isEmpty()) return none
        val s = all.minOf { it.lat } - 0.01
        val n = all.maxOf { it.lat } + 0.01
        val w = all.minOf { it.lon } - 0.01
        val e = all.maxOf { it.lon } + 0.01
        if (n - s > 0.9 || e - w > 0.9) return none
        val lines = try {
            TrafficClient(backendUrl).lines(s, w, n, e)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            return none
        }
        return routes.map { r -> RouteTrafficInfo(pieces(r.shape, lines), TrafficDelay.estimate(r.shape, lines).first) }
    }
}
