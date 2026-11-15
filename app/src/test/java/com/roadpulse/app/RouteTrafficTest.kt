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

/** Overture categories turned into clear kinds, wrong ones corrected or hidden, duplicates merged. */
class OvertureCategoriesTest {
    private fun kind(category: String?, name: String) = com.roadpulse.app.data.OvertureCategories.kind(category, name)

    @Test
    fun categoriesBecomeShortKinds() {
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.MALL, kind("shopping_mall", "M-City Jalan Ampang"))
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.CLINIC, kind("dental_clinic", "Klinik Gigi"))
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.FUEL, kind("fueling_station", "Petronas Ampang"))
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.HOTEL, kind("holiday_rental_home", "M City @ Jalan Ampang"))
    }

    @Test
    fun wrongOrMissingCategoriesUseTheNameOrShowNothing() {
        // Overture says "historic site" for a condo: the name says residence.
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.RESIDENCE, kind("historic_site", "M City SOHO Residence"))
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.CLINIC, kind(null, "M City Dental Clinic (Veneer, Bridge)"))
        assertEquals(com.roadpulse.app.data.CommunityPlaceKind.OFFICE, kind(null, "Starza Corporation Sdn Bhd"))
        // Nothing to go on: no kind rather than a wrong one.
        assertEquals(null, kind("historic_site", "M city"))
        assertEquals(null, kind("some_unknown_thing", "Lobby 2"))
    }

    @Test
    fun theSamePlaceFromTwoSourcesIsShownOnce() {
        fun p(name: String, lat: Double, lon: Double) = com.roadpulse.app.data.Place(name, "", GeoPoint(lat, lon))
        val s = com.roadpulse.app.data.PlaceSearch
        assertTrue(s.sameSpot(p("M-City", 3.1590, 101.7472), p("m city", 3.1595, 101.7472)))          // ~55 m, same name
        assertTrue(s.sameSpot(p("Crowne Plaza KL City Centre", 3.163, 101.7138), p("Crowne Plaza KL City Centre, an IHG Hotel", 3.1631, 101.7138)))
        assertTrue(!s.sameSpot(p("M City", 3.159, 101.7472), p("M City", 3.17, 101.7472)))             // 1.2 km apart
        assertTrue(!s.sameSpot(p("Klinik A", 3.159, 101.7472), p("Klinik B", 3.159, 101.7472)))         // different places
    }
}
