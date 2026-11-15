package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.TrafficLine
import com.roadpulse.app.nav.RouteTraffic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Traffic bars on route cards and traffic colours on the route line. */
class RouteTrafficTest {
    // A straight road east, about 1.1 km, points every ~110 m.
    private val shape = (0..10).map { GeoPoint(3.1, 101.6 + it * 0.001) }

    private fun line(level: String, fromIdx: Int, toIdx: Int, lat: Double = 3.1) =
        TrafficLine(1, level, 10, 60, (fromIdx..toIdx).map { listOf(lat, 101.6 + it * 0.001) })

    @Test
    fun aJamInTheMiddleIsOnePiece() {
        val pieces = RouteTraffic.pieces(shape, listOf(line("JAM", 4, 6)))
        assertEquals(1, pieces.size)
        assertEquals("JAM", pieces[0].level)
        assertEquals(0.4, pieces[0].from, 0.01)
        assertEquals(0.6, pieces[0].to, 0.01)
        val lines = RouteTraffic.polylines(shape, pieces)
        assertEquals(3, lines.single().second.size)
    }

    @Test
    fun jamWinsOverSlowAndFreeRoadsAreIgnored() {
        val pieces = RouteTraffic.pieces(shape, listOf(line("SLOW", 0, 5), line("JAM", 3, 5), line("FREE", 6, 10)))
        assertEquals(listOf("SLOW", "JAM"), pieces.map { it.level })
        assertEquals(0.3, pieces[1].from, 0.01)
    }

    @Test
    fun aCrossingOrParallelRoadFarAwayDoesNotCount() {
        assertTrue(RouteTraffic.pieces(shape, listOf(line("JAM", 2, 8, lat = 3.102))).isEmpty())
    }
}

/** Search results: places nearby first, far-away ones only after them. */
class SearchRankingTest {
    @Test
    fun nearbyPlacesComeBeforeFamousOnesAbroad() {
        val me = GeoPoint(3.2, 101.75) // Kuala Lumpur
        fun place(name: String, lat: Double, lon: Double) = com.roadpulse.app.data.Place(name, "", GeoPoint(lat, lon))
        val fromService = listOf(
            place("MCITy, USA", 32.35, -90.88),
            place("Mcity Inn, Miri", 4.39, 113.99),
            place("M City, Jalan Ampang", 3.16, 101.72),
            place("M City Kopitiam, Selayang", 3.25, 101.65),
        )
        val ranked = com.roadpulse.app.data.GeocodingClient.rankByDistance(fromService, me).map { it.name }
        assertEquals(listOf("M City, Jalan Ampang", "M City Kopitiam, Selayang", "Mcity Inn, Miri", "MCITy, USA"), ranked)
        // Without a position the service's order is kept.
        assertEquals(fromService, com.roadpulse.app.data.GeocodingClient.rankByDistance(fromService, null))
    }
}
