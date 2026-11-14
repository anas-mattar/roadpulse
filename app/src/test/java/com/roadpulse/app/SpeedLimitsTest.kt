package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Polyline
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.data.SpeedLimitSpan
import com.roadpulse.app.data.SpeedLimits
import com.roadpulse.app.ui.isSpeeding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedLimitsTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun polylineEncodeRoundTripsRealRoute() {
        val route = RoutingClient("x").parseRoute(res("route_kl_seremban.json"))
        val decoded = Polyline.decode(Polyline.encode(route.shape))
        assertEquals(route.shape.size, decoded.size)
        route.shape.zip(decoded).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
        }
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline.encode(
            listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453)), precision = 5,
        ))
    }

    @Test
    fun parsesRealSpeedLimitsAlongMotorwayRoute() {
        val route = RoutingClient("x").parseRoute(res("route_kl_seremban.json"))
        val limits = RoutingClient("x").parseSpeedLimits(res("speed_limits_kl_seremban.json"))
        assertFalse(limits.isEmpty)
        val segments = route.shape.indices.toList().dropLast(1)
        val known = segments.mapNotNull { limits.at(it) }
        assertTrue("coverage ${known.size}/${segments.size}", known.size > segments.size / 2)
        assertTrue(known.all { it in 20..130 })
        assertEquals(110, known.max()) // Malaysian expressway limit
    }

    @Test
    fun lookupUsesHalfOpenSpans() {
        val l = SpeedLimits(listOf(SpeedLimitSpan(10, 20, 60), SpeedLimitSpan(0, 5, 50), SpeedLimitSpan(20, 30, 80)))
        assertEquals(50, l.at(0))
        assertEquals(50, l.at(4))
        assertNull(l.at(5)) // gap: unknown limit
        assertEquals(60, l.at(19))
        assertEquals(80, l.at(20))
        assertNull(l.at(30))
    }

    @Test
    fun speedingNeedsMoreThanSmallTolerance() {
        assertFalse(isSpeeding(62, 60))
        assertFalse(isSpeeding(63, 60))
        assertTrue(isSpeeding(64, 60))
        assertFalse(isSpeeding(150, null))
    }
}
