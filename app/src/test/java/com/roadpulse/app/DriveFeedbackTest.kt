package com.roadpulse.app

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.i18n.EnglishStrings
import com.roadpulse.app.i18n.stepMetres
import com.roadpulse.app.nav.RouteGeometry
import com.roadpulse.app.nav.RouteTraffic
import com.roadpulse.app.nav.TrafficPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Changes after the first real drive on an iPhone. */
class DriveFeedbackTest {
    @Test
    fun distancesUnderAKilometreStepByFive() {
        assertEquals("120 m", EnglishStrings.distance(0.122))
        assertEquals("125 m", EnglishStrings.distance(0.123))
        assertEquals("5 m", EnglishStrings.distance(0.001))
        assertEquals("995 m", EnglishStrings.distance(0.996))
        assertEquals("1.0 km", EnglishStrings.distance(0.999)) // would round to 1000 m
        assertEquals("5.7 km", EnglishStrings.distance(5.7))
        assertEquals(null, stepMetres(1.2))
    }

    private val line = listOf(GeoPoint(3.0, 101.0), GeoPoint(3.0, 101.01), GeoPoint(3.01, 101.01))

    @Test
    fun onlyTheRouteAheadIsKept() {
        val total = Geo.distanceMeters(line[0], line[1]) + Geo.distanceMeters(line[1], line[2])
        // Start of the drive: the whole line.
        assertEquals(3, RouteGeometry.ahead(line, total).size)
        // Half-way along the first piece: starts between the first two points, keeps the corner.
        val half = RouteGeometry.ahead(line, total - Geo.distanceMeters(line[0], line[1]) / 2)
        assertEquals(3, half.size)
        assertEquals(101.005, half[0].lon, 1e-4)
        assertEquals(line[1], half[1])
        // On the second piece: the corner is behind and gone.
        val late = RouteGeometry.ahead(line, Geo.distanceMeters(line[1], line[2]) / 4)
        assertEquals(2, late.size)
        assertEquals(3.0075, late[0].lat, 1e-4)
        assertEquals(listOf(line.last()), RouteGeometry.ahead(line, 0.0))
    }

    @Test
    fun passedTrafficStretchesAreDropped() {
        val pieces = listOf(TrafficPiece(0.1, 0.2, "SLOW"), TrafficPiece(0.4, 0.6, "JAM"), TrafficPiece(0.8, 0.9, "SLOW"))
        assertEquals(pieces, RouteTraffic.ahead(pieces, 0.0))
        assertEquals(listOf(TrafficPiece(0.5, 0.6, "JAM"), TrafficPiece(0.8, 0.9, "SLOW")), RouteTraffic.ahead(pieces, 0.5))
        assertTrue(RouteTraffic.ahead(pieces, 0.95).isEmpty())
    }

    @Test
    fun rerouteAsksForTheDirectionOfTravel() {
        val client = RoutingClient("https://routing.example")
        val moving = client.requestBody(GeoPoint(3.0, 101.0), GeoPoint(3.1, 101.1), heading = 271.6)
        assertTrue(moving, moving.contains("\"heading\":272") && moving.contains("\"heading_tolerance\":60"))
        // Only the start has a heading, and a normal request has none.
        assertEquals(1, Regex("\"heading\":").findAll(moving).count())
        assertFalse(client.requestBody(GeoPoint(3.0, 101.0), GeoPoint(3.1, 101.1)).contains("heading"))
    }
}

/** "Report a problem with this place". */
class PlaceProblemTest {
    @Test
    fun placesFromTheServerDataCanBeReportedAndOthersCannot() {
        val listed = com.roadpulse.app.data.DirectoryPlace("o1", "IMAS International School KL", "education", "Jalan Gurney Kiri", 3.1588, 101.7105)
            .toPlace(EnglishStrings)
        assertEquals("o1", listed.directoryId)
        assertEquals(null, com.roadpulse.app.data.Place("Somewhere", "", GeoPoint(3.0, 101.0)).directoryId)
        // Saved places written before this field existed still load.
        val old = com.roadpulse.app.data.Http.json.decodeFromString(com.roadpulse.app.data.Place.serializer(),
            """{"name":"Home","description":"","location":{"lat":3.0,"lon":101.0}}""")
        assertEquals(null, old.directoryId)
    }

    @Test
    fun aWrongPositionNeedsThePositionOrANote() {
        val wrong = com.roadpulse.app.data.PlaceProblemKind.WRONG_POSITION
        val send = com.roadpulse.app.data.PlaceProblemsClient::canSend
        assertFalse(send(null, true, "note"))
        assertFalse(send(wrong, false, ""))
        assertFalse(send(wrong, false, " a "))
        assertTrue(send(wrong, true, ""))
        assertTrue(send(wrong, false, "It is on Jalan Gurney Kiri"))
        assertTrue(send(com.roadpulse.app.data.PlaceProblemKind.CLOSED, false, ""))
    }
}
