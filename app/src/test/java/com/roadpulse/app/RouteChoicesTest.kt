package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.RouteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteChoicesTest {
    private fun route(minutes: Int, km: Double, toll: Boolean, vararg roads: Pair<String, Double>) = Route(
        shape = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.1, 0.1)),
        maneuvers = roads.map { (name, len) -> Maneuver("x", len, 60.0, 0, streetNames = listOf(name)) },
        lengthKm = km, timeSeconds = minutes * 60.0, hasToll = toll,
    )

    @Test
    fun tagsAndDistinctiveRoads() {
        val routes = listOf(
            route(19, 11.3, true, "Jalan Damansara" to 2.0, "E37" to 7.0, "Jalan Ampang" to 2.3),
            route(23, 10.1, false, "Jalan Damansara" to 2.0, "Jalan Tun Razak" to 6.0, "Jalan Ampang" to 2.1),
            route(26, 9.5, true, "Jalan Damansara" to 2.0, "Jalan Pudu" to 5.5, "Jalan Ampang" to 2.0),
        )
        val c = RouteChoices.describe(routes)
        assertEquals("Fastest", c[0].tag)
        assertEquals("E37", c[0].via)
        assertEquals("No tolls", c[1].tag)
        assertEquals("Jalan Tun Razak", c[1].via)
        assertEquals("Shortest", c[2].tag)
        assertEquals("Jalan Pudu", c[2].via)
    }

    @Test
    fun extraMinutesWhenNothingElseStandsOut() {
        val c = RouteChoices.describe(
            listOf(route(30, 25.0, false, "A" to 25.0), route(37, 27.0, false, "B" to 27.0)),
        )
        assertEquals("+7 min", c[1].tag)
    }

    @Test
    fun parsesRealAlternatesResponse() {
        val body = javaClass.classLoader!!.getResource("route_alternates_subang.json")!!.readText()
        val routes = RoutingClient("x").parseRoutes(body)
        assertEquals(3, routes.size)
        assertTrue(routes.all { it.shape.size > 10 && it.maneuvers.isNotEmpty() })
        assertFalse(routes[0].hasToll) // fastest Subang route avoids tolls, the others use them
        assertTrue(routes[1].hasToll)
        val choices = RouteChoices.describe(routes)
        assertEquals("Fastest", choices[0].tag)
        assertNotEquals(choices[1].via, choices[0].via)
    }

    @Test
    fun rerouteRequestOmitsAlternates() {
        val body = RoutingClient("x").requestBody(GeoPoint(3.1, 101.6), GeoPoint(3.2, 101.7))
        assertFalse(body, body.contains("alternates"))
    }
}
