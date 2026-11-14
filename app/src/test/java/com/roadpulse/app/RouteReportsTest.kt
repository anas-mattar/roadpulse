package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import com.roadpulse.app.nav.ReportAhead
import com.roadpulse.app.nav.ReportAnnouncer
import com.roadpulse.app.nav.RouteReports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteReportsTest {
    // A straight road east along latitude 3.0, about 2.2 km long (0.02° of longitude).
    private val shape = listOf(GeoPoint(3.0, 101.60), GeoPoint(3.0, 101.61), GeoPoint(3.0, 101.62))
    private val metersPerDegLon = 111_195.0 * Math.cos(Math.toRadians(3.0))

    private fun report(id: String, lat: Double, lon: Double, type: ReportType = ReportType.POLICE) =
        Report(id, type, GeoPoint(lat, lon), 0)

    @Test
    fun onlyReportsOnTheRouteAndAheadOfTheCarCount() {
        val rr = RouteReports(shape)
        val reports = listOf(
            report("behind", 3.0, 101.602),
            report("ahead-near", 3.0001, 101.608),       // 11 m off the road: on the route
            report("ahead-far", 3.0, 101.6175),
            report("side-street", 3.002, 101.609),      // 220 m off the road
        )
        val car = 0.005 * metersPerDegLon // about 555 m along
        val ahead = rr.ahead(reports, car, maxAheadM = 1000.0)
        assertEquals(listOf("ahead-near"), ahead.map { it.report.id }) // "ahead-far" is ~1.4 km away
        assertEquals(0.003 * metersPerDegLon, ahead[0].metersAhead, 2.0)
        assertNull(rr.alongOf(reports[3]))
        assertEquals(listOf("ahead-near", "ahead-far"), rr.ahead(reports, car, 2000.0).map { it.report.id })
    }

    @Test
    fun eachReportIsAnnouncedOnceWhenItComesWithin800m() {
        val announcer = ReportAnnouncer()
        val police = report("p", 3.0, 101.61)
        assertNull(announcer.next(listOf(ReportAhead(police, 950.0))))
        assertEquals("p", announcer.next(listOf(ReportAhead(police, 780.0)))?.report?.id)
        assertNull(announcer.next(listOf(ReportAhead(police, 500.0)))) // already said
        val crash = report("c", 3.0, 101.615, ReportType.CRASH)
        assertEquals("c", announcer.next(listOf(ReportAhead(police, 400.0), ReportAhead(crash, 700.0)))?.report?.id)
    }

    @Test
    fun spokenDistancesAreRoundedToHundreds() {
        assertEquals(800, ReportAnnouncer.spokenMeters(780.0))
        assertEquals(700, ReportAnnouncer.spokenMeters(749.0))
        assertEquals(100, ReportAnnouncer.spokenMeters(30.0))
    }

    @Test
    fun warningsInBothLanguages() {
        assertEquals("Police reported 700 meters ahead.", EnglishNarrator.reportAhead(ReportType.POLICE, 700))
        assertEquals("Road closure reported 300 meters ahead.", EnglishNarrator.reportAhead(ReportType.ROAD_CLOSED, 300))
        assertEquals("Kemalangan dilaporkan 500 meter di hadapan.", MalayNarrator.reportAhead(ReportType.CRASH, 500))
    }
}
