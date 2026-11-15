package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Lane
import com.roadpulse.app.data.OpenStatus
import com.roadpulse.app.data.OpeningHours
import com.roadpulse.app.data.status
import com.roadpulse.app.data.toPlace
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.RouteGeometry
import com.roadpulse.app.nav.SchoolAhead
import com.roadpulse.app.nav.SchoolZoneAnnouncer
import com.roadpulse.app.nav.TurnIcon
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LanesAndSchoolsTest {
    @Test
    fun routeRequestAsksForLanesAndReadsThem() = runBlocking {
        val body = RoutingClient("http://unused").requestBody(GeoPoint(3.139, 101.6869), GeoPoint(3.07, 101.6))
        assertTrue(body, body.contains("\"turn_lanes\":true"))

        // A real answer from Valhalla (KL Sentral to Sunway Pyramid) with lane data.
        val json = javaClass.classLoader!!.getResource("valhalla_route_with_lanes.json")!!.readText()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(json))
        server.start()
        val route = RoutingClient(server.url("").toString().trimEnd('/')).routes(GeoPoint(3.139, 101.6869), GeoPoint(3.07, 101.6), alternates = 0).first()
        server.shutdown()
        val withLanes = route.maneuvers.filter { it.lanes.isNotEmpty() }
        assertEquals(5, withLanes.size)
        val keepLeft = withLanes.first { it.instruction.startsWith("Keep left to stay on E10") }
        assertEquals(listOf(true, true, false, false), keepLeft.lanes.map { it.recommended })
        assertEquals(Lane.SLIGHT_LEFT, keepLeft.lanes[0].arrow)
        assertEquals(Lane.SLIGHT_RIGHT, keepLeft.lanes[3].arrow)
        assertTrue(Lane.worthShowing(keepLeft.lanes))
    }

    @Test
    fun laneArrows() {
        val l = Lane(directions = Lane.THROUGH or Lane.LEFT, valid = Lane.LEFT)
        assertTrue(l.usable)
        assertFalse(l.recommended)
        assertEquals(Lane.LEFT, l.arrow)
        assertEquals(Lane.THROUGH, Lane(Lane.THROUGH).arrow)
        assertEquals(TurnIcon.UTURN_RIGHT, TurnIcon.forLane(Lane.REVERSE))
        assertEquals(TurnIcon.SLIGHT_LEFT, TurnIcon.forLane(Lane.SLIGHT_LEFT))
        // Every lane recommended: no choice to make, so nothing is shown.
        assertFalse(Lane.worthShowing(listOf(Lane(2, active = 2), Lane(2, active = 2))))
        assertFalse(Lane.worthShowing(listOf(Lane(2, active = 2))))
    }

    @Test
    fun schoolZoneWarningsAreGroupedAndSkippedOnFastRoads() {
        val a = SchoolZoneAnnouncer()
        assertFalse(a.shouldWarn(SchoolAhead("s1", 450.0, 1450.0), 1000.0, 50)) // too far yet
        assertTrue(a.shouldWarn(SchoolAhead("s1", 250.0, 1450.0), 1200.0, 50))
        assertFalse(a.shouldWarn(SchoolAhead("s2", 200.0, 1700.0), 1500.0, 50)) // same stretch
        assertTrue(a.shouldWarn(SchoolAhead("s3", 200.0, 2400.0), 2200.0, null))
        assertFalse(SchoolZoneAnnouncer().shouldWarn(SchoolAhead("s4", 100.0, 500.0), 400.0, 90)) // expressway
    }

    @Test
    fun remainingRouteStartsAtTheCar() {
        val shape = listOf(GeoPoint(3.0, 101.0), GeoPoint(3.0, 101.01), GeoPoint(3.0, 101.02), GeoPoint(3.0, 101.03))
        val rest = RouteGeometry.remaining(shape, GeoPoint(3.0001, 101.0102))
        assertEquals(listOf(GeoPoint(3.0001, 101.0102), GeoPoint(3.0, 101.02), GeoPoint(3.0, 101.03)), rest)
        assertEquals(shape, RouteGeometry.remaining(shape, null))
    }

    // Friday 15 November 2026
    private fun at(h: Int, m: Int = 0, day: Int = 13) = LocalDateTime.of(2026, 11, day, h, m)

    @Test
    fun openingHours() {
        assertEquals(OpenStatus.AlwaysOpen, OpeningHours.status("24/7", at(3)))
        assertEquals(OpenStatus.AlwaysOpen, OpeningHours.status("Mo-Su 00:00-24:00", at(3)))
        val office = "Mo-Fr 08:00-18:00; Sa 09:00-13:00; Su off"
        assertEquals(OpenStatus.Open(com.roadpulse.app.data.ClockTime(18, 0)), OpeningHours.status(office, at(10)))         // Friday
        assertEquals(OpenStatus.Closed(com.roadpulse.app.data.ClockTime(9, 0)), OpeningHours.status(office, at(19)))        // Fri evening → Sat 9:00
        assertEquals(OpenStatus.Closed(null), OpeningHours.status(office, at(14, day = 14)))                       // Sat afternoon → Mon
        assertEquals(OpenStatus.Closed(com.roadpulse.app.data.ClockTime(8, 0)), OpeningHours.status(office, at(6)))
        // Overnight: open at 01:00 on Saturday from Friday's period.
        val mamak = "Mo-Su 18:00-03:00"
        assertEquals(OpenStatus.Open(com.roadpulse.app.data.ClockTime(3, 0)), OpeningHours.status(mamak, at(1, day = 14)))
        assertEquals(OpenStatus.Closed(com.roadpulse.app.data.ClockTime(18, 0)), OpeningHours.status(mamak, at(12)))
        // Split hours and lists.
        val lunch = "Mo,We,Fr 11:00-14:00, 18:00-22:00"
        assertEquals(OpenStatus.Open(com.roadpulse.app.data.ClockTime(22, 0)), OpeningHours.status(lunch, at(20)))
        assertEquals(OpenStatus.Closed(com.roadpulse.app.data.ClockTime(18, 0)), OpeningHours.status(lunch, at(15)))
        // Times without days apply every day; a later rule replaces an earlier one.
        assertEquals(OpenStatus.Open(com.roadpulse.app.data.ClockTime(22, 0)), OpeningHours.status("08:00-22:00", at(9, day = 15)))
        assertEquals(OpenStatus.Closed(com.roadpulse.app.data.ClockTime(8, 0)), OpeningHours.status("Mo-Su 08:00-22:00; Fr off", at(9))) // Friday off → Sat 08:00
        // Not understood: nothing shown.
        assertNull(OpeningHours.status("Jan-Mar Mo-Fr 08:00-17:00", at(9)))
        assertNull(OpeningHours.status("sunrise-sunset", at(9)))
        assertNull(OpeningHours.status(null, at(9)))
        assertNull(OpeningHours.status("Mo-Fr 18:00+", at(9)))
    }

    @Test
    fun placeListShowsOpenStatus() {
        val p = com.roadpulse.app.data.MapPlace("n1", com.roadpulse.app.data.PlaceCategory.FUEL, name = "Petronas", lat = 3.0, lon = 101.0,
            distanceM = 240.0, openingHours = "Mo-Su 06:00-23:00")
        assertEquals("240 m · Open · closes 23:00", p.toPlace(now = at(10)).description)
        assertEquals("240 م · مفتوح · يغلق 23:00", p.toPlace(com.roadpulse.app.i18n.ArabicStrings, at(10)).description)
        assertEquals("240 m · Closed · opens 06:00", p.toPlace(now = at(23, 30)).description)
    }
}
