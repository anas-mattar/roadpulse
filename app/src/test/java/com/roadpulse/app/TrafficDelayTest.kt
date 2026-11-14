package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.data.TrafficLine
import com.roadpulse.app.i18n.ArabicStrings
import com.roadpulse.app.i18n.EnglishStrings
import com.roadpulse.app.nav.TrafficDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficDelayTest {
    // A straight route east along lat 3.1, about 3.3 km.
    private val route = (0..30).map { GeoPoint(3.1, 101.60 + it * 0.001) }

    private fun line(lonFrom: Double, lonTo: Double, kph: Int, level: String = "JAM", lat: Double = 3.1) =
        TrafficLine(1, level, kph, 60, listOf(listOf(lat, lonFrom), listOf(lat, (lonFrom + lonTo) / 2), listOf(lat, lonTo)))

    @Test
    fun jamOnTheRouteAddsItsExtraTime() {
        // ~1.1 km at 10 km/h instead of 60: about 400 s slower minus 67 s normal ≈ 333 s.
        val (delay, hit) = TrafficDelay.estimate(route, listOf(line(101.605, 101.615, 10)))
        assertEquals(333.0, delay, 15.0)
        assertEquals(1, hit.size)
    }

    @Test
    fun aJamOnACrossingOrParallelRoadIsIgnored() {
        val crossing = TrafficLine(2, "JAM", 5, 60, listOf(listOf(3.095, 101.61), listOf(3.1, 101.61), listOf(3.105, 101.61)))
        val parallel = line(101.605, 101.615, 5, lat = 3.1009) // ~100 m north
        val (delay, hit) = TrafficDelay.estimate(route, listOf(crossing, parallel))
        assertEquals(0.0, delay, 0.001)
        assertTrue(hit.isEmpty())
    }

    @Test
    fun avoidAreasAreClosedBoxesAroundJamsOnly() {
        val areas = TrafficDelay.avoidAreas(listOf(line(101.605, 101.615, 10), line(101.62, 101.625, 30, level = "SLOW")))
        val box = areas.single()
        assertEquals(5, box.size)
        assertEquals(box.first(), box.last())
        assertTrue(box.minOf { it.lon } < 101.605 && box.maxOf { it.lon } > 101.615)
        val body = RoutingClient("http://unused").requestBody(route.first(), route.last(), avoid = areas)
        assertTrue(body, body.contains("\"exclude_polygons\":[[["))
        assertTrue(!RoutingClient("http://unused").requestBody(route.first(), route.last()).contains("exclude_polygons"))
    }

    @Test
    fun aheadIsLimitedInLength() {
        val ahead = TrafficDelay.ahead(route, GeoPoint(3.1, 101.60), maxM = 1_000.0)
        assertTrue(ahead.size in 10..12)
    }

    @Test
    fun privacyTextsInBothLanguages() {
        assertEquals(EnglishStrings.privacyPolicyText.size, ArabicStrings.privacyPolicyText.size)
        assertTrue(ArabicStrings.privacyPolicyText.none { it.isBlank() })
    }
}
