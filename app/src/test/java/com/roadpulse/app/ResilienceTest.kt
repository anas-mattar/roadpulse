package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.DriveStore
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.PromptText
import com.roadpulse.app.nav.RouteTracker
import com.roadpulse.app.nav.SignalMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResilienceTest {
    private val shape = (0..40).map { GeoPoint(3.0 + it * 0.0005, 101.0) } // ~2.2 km north
    private val route = Route(
        shape,
        listOf(Maneuver("Drive north.", 1.1, 100.0, 0, verbalPre = "Drive north."), Maneuver("Turn right.", 1.1, 100.0, 20)),
        2.2, 200.0,
    )
    private val place = Place("M City", "Kuala Lumpur", shape.last())

    // --- DriveStore ---

    @Test
    fun savedDriveRoundTrips() {
        val file = File.createTempFile("drive", ".json").apply { delete() }
        val store = DriveStore(file, now = { 1_000_000L })
        assertNull(store.load())
        store.save(route, place)
        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(route, loaded!!.route)
        assertEquals(place, loaded.destination)
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun staleOrCorruptDrivesAreDiscarded() {
        val file = File.createTempFile("drive", ".json").apply { delete() }
        var clock = 0L
        val store = DriveStore(file, maxAgeMs = 60_000, now = { clock })
        store.save(route, place)
        clock = 61_000
        assertNull(store.load())
        assertFalse(file.exists())

        file.writeText("{ not json")
        assertNull(store.load())
        assertFalse(file.exists())
    }

    // --- Resumed drive matching ---

    @Test
    fun resumedTrackerFindsPositionMidRoute() {
        val fresh = RouteTracker(route)
        val resumed = RouteTracker(route, resumed = true)
        val mid = LocationFix(shape[30], 5.0, 0.0, 15.0, 1_000)
        // A fresh drive expects the start, so a fix 1.6 km along is outside its search window...
        assertFalse(fresh.update(mid).matched)
        // ...while a resumed drive searches the whole route and carries on.
        val p = resumed.update(mid)
        assertTrue(p.matched)
        assertEquals(2224.0 - 1668.0, p.remainingMeters, 10.0)
        assertFalse(p.offRoute)
    }

    // --- SignalMonitor ---

    @Test
    fun gpsLostAfterSilenceAndRestoredOnUsableFix() {
        val m = SignalMonitor(lostAfterMs = 6_000)
        m.onFix(0, usable = true)
        assertFalse(m.check(5_000))
        assertTrue(m.check(6_000))   // newly lost
        assertFalse(m.check(7_000))  // reported once
        assertFalse(m.onFix(8_000, usable = false)) // poor fix does not restore
        assertTrue(m.lost)
        assertTrue(m.onFix(9_000, usable = true))   // restored
        assertFalse(m.lost)
    }

    @Test
    fun gpsLostWhenNoFixEverArrives() {
        val m = SignalMonitor(lostAfterMs = 6_000)
        assertFalse(m.check(100_000)) // first check starts the timer
        assertTrue(m.check(106_000))
    }

    // --- PromptText ---

    @Test
    fun tidiesCapitalAfterThenButKeepsNames() {
        assertEquals(
            "Bear left. Then your destination will be on the left.",
            PromptText.tidy("Bear left. Then Your destination will be on the left."),
        )
        assertEquals("Turn left. Then turn right.", PromptText.tidy("Turn left. Then Turn right."))
        assertEquals("Then Jalan Ampang.", PromptText.tidy("Then Jalan Ampang."))
    }
}
