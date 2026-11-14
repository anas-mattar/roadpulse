package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.RouteTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {
    // Straight line due north, one vertex every ~55.6 m (0.0005 deg latitude), 41 vertices = ~2.2 km.
    private val shape = (0..40).map { GeoPoint(3.0 + it * 0.0005, 101.0) }
    private val route = Route(
        shape = shape,
        maneuvers = listOf(
            Maneuver("Drive north.", 1.1, 100.0, 0),
            Maneuver("Turn right onto Jalan A.", 1.1, 100.0, 20, verbalAlert = "Turn right onto Jalan A.", verbalPre = "Turn right onto Jalan A now."),
            Maneuver("You have arrived.", 0.0, 0.0, 40),
        ),
        lengthKm = 2.2,
        timeSeconds = 200.0,
    )

    @Test
    fun reportsDistanceAndRemainingTimeAtStart() {
        val p = RouteTracker(route).update(shape[0])
        assertEquals("Turn right onto Jalan A.", p.upcoming?.instruction)
        assertEquals(1112.0, p.distanceToTurnMeters, 5.0)
        assertEquals(200.0, p.remainingSeconds, 0.5)
        assertNull(p.prompt)
        assertFalse(p.arrived)
    }

    @Test
    fun speaksAlertOnceThenFinalPromptOnce() {
        val tracker = RouteTracker(route)
        assertNull(tracker.update(shape[5]).prompt)                       // ~830 m before the turn
        assertEquals("In 450 meters, turn right onto Jalan A.", tracker.update(shape[12]).prompt)
        assertNull(tracker.update(shape[13]).prompt)                      // not repeated
        assertEquals("Turn right onto Jalan A now.", tracker.update(shape[19]).prompt)
        assertNull(tracker.update(shape[19]).prompt)
    }

    @Test
    fun singleOffRouteFixIsNotEnoughThenArrivalAtEnd() {
        val tracker = RouteTracker(route)
        tracker.update(shape[0])
        val off = tracker.update(GeoPoint(3.0005, 101.002)) // one fix ~220 m east of the line
        assertTrue(off.offRouteMeters > RouteTracker.OFF_ROUTE_M)
        assertFalse(off.offRoute)
        assertNull(off.prompt)
        var last = off
        for (i in 1..40) last = tracker.update(shape[i])
        assertTrue(last.arrived)
    }
}
