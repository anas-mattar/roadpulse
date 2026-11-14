package com.roadpulse.app

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Polyline
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos

/**
 * Replays a real 60 km Kuala Lumpur -> Seremban route shape (Valhalla, OpenStreetMap data,
 * © OpenStreetMap contributors) with simulated GPS noise, and expects zero false reroutes.
 */
class RealRouteReplayTest {

    @Test
    fun noFalseReroutesOnRealHighwayRouteWithGpsNoise() {
        val encoded = javaClass.classLoader!!.getResource("kl_seremban_shape.txt")!!.readText().trim()
        val shape = Polyline.decode(encoded, 6)
        val route = Route(shape, listOf(Maneuver("Start", 60.0, 3600.0, 0)), 60.0, 3600.0)
        val tracker = RouteTracker(route)
        val random = Random(42)

        // Walk the shape at 25 m/s, one fix per second, with ~8 m Gaussian position noise.
        var t = 0L
        var falseReroutes = 0
        var fixes = 0
        var arrived = false
        for (i in 0 until shape.size - 1) {
            val a = shape[i]
            val b = shape[i + 1]
            val len = Geo.distanceMeters(a, b)
            val steps = maxOf(1, (len / 25.0).toInt())
            val bearing = bearing(a, b)
            for (s in 0 until steps) {
                val f = s.toDouble() / steps
                val lat = a.lat + (b.lat - a.lat) * f + random.nextGaussian() * 8 / 111_195.0
                val lon = a.lon + (b.lon - a.lon) * f +
                    random.nextGaussian() * 8 / (111_195.0 * cos(Math.toRadians(a.lat)))
                val p = tracker.update(LocationFix(GeoPoint(lat, lon), 8.0, bearing + random.nextGaussian() * 5, 25.0, t))
                if (p.offRoute) falseReroutes++
                fixes++
                t += 1000
            }
        }
        val end = shape.last()
        repeat(3) {
            arrived = arrived || tracker.update(LocationFix(end, 5.0, null, 0.0, t)).arrived
            t += 1000
        }
        assertTrue("expected a long replay, got $fixes fixes", fixes > 2000)
        assertEquals(0, falseReroutes)
        assertTrue(arrived)
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians(a.lat))
        return (Math.toDegrees(atan2((b.lon - a.lon) * k, b.lat - a.lat)) + 360) % 360
    }
}
