package com.roadpulse.app

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.OffRouteDetector
import com.roadpulse.app.nav.RouteMatcher
import com.roadpulse.app.nav.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMatcherTest {

    private fun fix(lat: Double, lon: Double, t: Long, bearing: Double? = null, speed: Double? = null, acc: Double = 5.0) =
        LocationFix(GeoPoint(lat, lon), acc, bearing, speed, t)

    private fun route(shape: List<GeoPoint>, maneuvers: List<Maneuver> = listOf(Maneuver("Go", 0.0, 0.0, 0))) =
        Route(shape, maneuvers, 0.0, 600.0)

    /** Northbound highway with vertices ~1.08 km apart, like real routing shapes on straight roads. */
    private val highway = listOf(GeoPoint(3.0, 101.0), GeoPoint(3.0097, 101.0), GeoPoint(3.0194, 101.0))

    @Test
    fun noFalseRerouteBetweenDistantVertices() {
        val tracker = RouteTracker(route(highway))
        var maxOffset = 0.0
        // Drive the whole highway at 30 m/s with one fix per second.
        for (s in 0..70) {
            val p = tracker.update(fix(3.0 + s * 30 / 111_195.0, 101.0, s * 1000L, bearing = 0.0, speed = 30.0))
            assertFalse("false off-route at ${s * 30} m", p.offRoute)
            assertTrue(p.matched)
            maxOffset = maxOf(maxOffset, p.offRouteMeters)
        }
        assertTrue(maxOffset < 1.0)
    }

    @Test
    fun oldVertexOnlyCheckWouldHaveFailedHere() {
        // Documents the bug fixed by segment matching: midway, the nearest vertex is ~540 m away.
        val mid = GeoPoint(3.00485, 101.0)
        assertTrue(highway.minOf { Geo.distanceMeters(it, mid) } > 500)
        val m = RouteMatcher(highway).match(fix(mid.lat, mid.lon, 0))
        assertTrue(m.onRoute)
        assertEquals(540.0, m.distanceAlongM, 5.0)
    }

    @Test
    fun hysteresisNeedsSeveralFixesAndSeconds() {
        val d = OffRouteDetector(minBadFixes = 3, minBadMillis = 4_000)
        assertFalse(d.onFix(false, 0))
        assertFalse(d.onFix(false, 1_000))
        assertFalse(d.onFix(false, 2_000))   // 3 fixes but only 2 s
        assertTrue(d.onFix(false, 4_000))    // 4 fixes over 4 s
        assertFalse(d.onFix(true, 5_000))    // back on route resets
        assertFalse(d.onFix(false, 6_000))
    }

    @Test
    fun leavingTheRouteTriggersRerouteAfterHysteresis() {
        val tracker = RouteTracker(route(highway))
        for (s in 0..10) tracker.update(fix(3.0 + s * 20 / 111_195.0, 101.0, s * 1000L, 0.0, 20.0))
        // Car turns off east onto another road.
        val results = (1..6).map { k ->
            tracker.update(fix(3.0018, 101.0 + k * 20 / 111_195.0, (10 + k) * 1000L, bearing = 90.0, speed = 20.0))
        }
        assertFalse(results[0].offRoute) // 20 m east: still within tolerance
        assertTrue(results.last().offRoute)
        assertFalse(results.last().matched)
    }

    @Test
    fun oppositeCarriagewayIsNotOnRoute() {
        val m = RouteMatcher(highway)
        m.match(fix(3.0, 101.0, 0, 0.0, 25.0))
        // 15 m east of the northbound route line, but driving south.
        val r = m.match(fix(3.001, 101.000135, 1000, bearing = 180.0, speed = 25.0))
        assertTrue(r.offsetM < 20)
        assertFalse(r.onRoute)
    }

    @Test
    fun uTurnIsDetectedAsOffRoute() {
        val tracker = RouteTracker(route(highway))
        for (s in 0..20) tracker.update(fix(3.0 + s * 15 / 111_195.0, 101.0, s * 1000L, 0.0, 15.0))
        var last = tracker.update(fix(3.0027, 101.0, 21_000, 180.0, 10.0))
        for (s in 1..5) last = tracker.update(fix(3.0027 - s * 10 / 111_195.0, 101.0, (21 + s) * 1000L, 180.0, 10.0))
        assertTrue(last.offRoute)
    }

    @Test
    fun outAndBackRouteDoesNotJumpToTheReturnLeg() {
        // North 1 km, a short hop east, then back south on a road 40 m away.
        val east = 40 / (111_195.0 * Math.cos(Math.toRadians(3.0)))
        val shape = listOf(
            GeoPoint(3.0, 101.0), GeoPoint(3.009, 101.0),
            GeoPoint(3.009, 101.0 + east), GeoPoint(3.0, 101.0 + east),
        )
        val m = RouteMatcher(shape)
        m.match(fix(3.0, 101.0, 0, 0.0, 15.0))
        // GPS drifts 25 m east: now closer to the return leg (15 m) than the outbound leg (25 m).
        val drift = 25 / (111_195.0 * Math.cos(Math.toRadians(3.0)))
        val r = m.match(fix(3.002, 101.0 + drift, 15_000, bearing = 0.0, speed = 15.0))
        assertEquals(0, r.segmentIndex)
        assertTrue(r.distanceAlongM < 1000)
        assertTrue(r.onRoute)
    }

    @Test
    fun poorAccuracyFixesAreIgnored() {
        val tracker = RouteTracker(route(highway))
        tracker.update(fix(3.0, 101.0, 0, 0.0, 20.0))
        var last = tracker.update(fix(3.0002, 101.0, 1000, 0.0, 20.0))
        // Ten wildly wrong, low-accuracy fixes (e.g. in a tunnel).
        for (s in 2..11) last = tracker.update(fix(3.0, 101.01, s * 1000L, acc = 150.0))
        assertTrue(last.poorSignal)
        assertFalse(last.offRoute)
        // Signal returns further along the road: matching resumes without a reroute.
        val back = tracker.update(fix(3.0025, 101.0, 12_000, 0.0, 20.0))
        assertTrue(back.matched)
        assertFalse(back.offRoute)
    }

    @Test
    fun angleDiffWrapsAround() {
        assertEquals(20.0, RouteMatcher.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(180.0, RouteMatcher.angleDiff(0.0, 180.0), 1e-9)
    }
}
