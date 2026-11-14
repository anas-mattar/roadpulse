package com.roadpulse.app

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.DriveSimulator
import com.roadpulse.app.nav.NavLog
import com.roadpulse.app.nav.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class DriveSimulatorTest {
    // ~1.1 km north, then ~1.1 km east.
    private val shape = listOf(GeoPoint(3.0, 101.0), GeoPoint(3.01, 101.0), GeoPoint(3.01, 101.01))

    @Test
    fun advancesAtConstantSpeedAndFinishes() {
        val sim = DriveSimulator(shape, speedMps = 20.0, noiseM = 0.0)
        repeat(10) { sim.next(it * 1000L) }
        assertEquals(200.0, sim.alongM, 1e-6)
        assertEquals(0.0, Geo.distanceMeters(sim.position(), GeoPoint(3.0 + 200 / 111_195.0, 101.0)), 1.0)
        while (!sim.finished) sim.next(0)
        assertEquals(sim.totalMeters, sim.alongM, 1e-6)
        assertEquals(shape.last().lat, sim.position().lat, 1e-9)
    }

    @Test
    fun bearingFollowsTheRoad() {
        val sim = DriveSimulator(shape, speedMps = 20.0, noiseM = 0.0)
        sim.next(0)
        assertEquals(0.0, sim.bearing(), 0.5)
        while (sim.alongM < 1500) sim.next(0)
        assertEquals(90.0, sim.bearing(), 0.5)
    }

    @Test
    fun noiseHasRequestedSpread() {
        val sim = DriveSimulator(listOf(GeoPoint(3.0, 101.0), GeoPoint(3.5, 101.0)), 10.0, noiseM = 8.0, seed = 7)
        var sumSq = 0.0
        val n = 2000
        repeat(n) {
            val f = sim.next(it * 1000L)
            val exact = sim.position()
            val d = Geo.distanceMeters(exact, f.position)
            sumSq += d * d
        }
        // 2-D Gaussian with 8 m per axis: RMS distance = 8 * sqrt(2).
        assertEquals(8.0 * sqrt(2.0), sqrt(sumSq / n), 0.6)
    }

    @Test
    fun offsetMovesTheRightDistance() {
        val p = GeoPoint(3.1, 101.6)
        assertEquals(500.0, Geo.distanceMeters(p, DriveSimulator.offset(p, 90.0, 500.0)), 1.0)
        assertEquals(500.0, Geo.distanceMeters(p, DriveSimulator.offset(p, 225.0, 500.0)), 1.0)
    }

    @Test
    fun simulatedDriveArrivesWithoutRerouteAndIsLogged() {
        val route = Route(shape, listOf(Maneuver("Go", 1.1, 60.0, 0), Maneuver("Turn right.", 1.1, 60.0, 1)), 2.2, 120.0)
        val tracker = RouteTracker(route)
        val sim = DriveSimulator(shape, speedMps = 14.0, noiseM = 8.0)
        val file = File.createTempFile("drive", ".csv")
        NavLog(file).use { log ->
            var t = 0L
            var arrived = false
            while (!arrived && t < 400_000) {
                t += 1000
                val fix = sim.next(t)
                val p = tracker.update(fix)
                log.fix(fix, p)
                p.prompt?.let { log.event(t, "prompt", it) }
                assertFalse("unexpected off-route at ${sim.alongM} m", p.offRoute)
                arrived = p.arrived
            }
            assertTrue(arrived)
            assertEquals(2, log.prompts) // 500 m alert + final prompt for the right turn
        }
        val lines = file.readLines()
        assertTrue(lines.first().startsWith("time_ms,event,lat,lon"))
        assertTrue(lines.any { it.contains(",prompt,") && it.contains("Turn right") })
        assertTrue(lines.last().contains(",summary,") && lines.last().contains("reroutes=0"))
        file.delete()
    }

    @Test
    fun detourIsDetectedAsOffRoute() {
        val route = Route(shape, listOf(Maneuver("Go", 2.2, 120.0, 0)), 2.2, 120.0)
        val tracker = RouteTracker(route)
        val sim = DriveSimulator(shape, speedMps = 14.0, noiseM = 8.0)
        var t = 0L
        while (sim.alongM < 400) { t += 1000; tracker.update(sim.next(t)) }
        val start = sim.position()
        var offRoute = false
        for (k in 1..30) {
            t += 1000
            val p = DriveSimulator.offset(start, 90.0, k * 14.0)
            offRoute = offRoute || tracker.update(sim.noisyFix(p, 90.0, 14.0, t)).offRoute
        }
        assertTrue(offRoute)
    }
}
