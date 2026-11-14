package com.roadpulse.app

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.RouteTracker
import com.roadpulse.app.nav.TurnIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TurnGuidanceTest {
    @Test
    fun iconsForCommonManeuvers() {
        assertEquals(TurnIcon.RIGHT, TurnIcon.forType(10))
        assertEquals(TurnIcon.LEFT, TurnIcon.forType(15))
        assertEquals(TurnIcon.KEEP_LEFT, TurnIcon.forType(24))
        assertEquals(TurnIcon.RAMP_LEFT, TurnIcon.forType(21))
        assertEquals(TurnIcon.ROUNDABOUT, TurnIcon.forType(26))
        assertEquals(TurnIcon.ARRIVE, TurnIcon.forType(6))
        assertEquals(TurnIcon.UTURN_RIGHT, TurnIcon.forType(12))
        assertEquals(TurnIcon.STRAIGHT, TurnIcon.forType(8))
        assertEquals(TurnIcon.STRAIGHT, TurnIcon.forType(null))
        assertEquals(TurnIcon.STRAIGHT, TurnIcon.forType(99))
    }

    @Test
    fun everyManeuverOnRealRoutesGetsAnIcon() {
        for (file in listOf("route_putrajaya.json", "route_kl_seremban.json")) {
            val route = RoutingClient("x").parseRoute(javaClass.classLoader!!.getResource(file)!!.readText())
            route.maneuvers.forEach { TurnIcon.forType(it.type) } // must not throw
            assertEquals(TurnIcon.ARRIVE, TurnIcon.forType(route.maneuvers.last().type))
        }
    }

    @Test
    fun trackerReportsFollowingTurnAndGap() {
        // ~55.6 m between vertices, northbound; turns at vertex 20 and 23, arrival at 40.
        val shape = (0..40).map { GeoPoint(3.0 + it * 0.0005, 101.0) }
        val route = Route(
            shape,
            listOf(
                Maneuver("Drive north.", 1.1, 60.0, 0, type = 1),
                Maneuver("Turn right.", 0.2, 10.0, 20, type = 10),
                Maneuver("Turn left.", 0.9, 50.0, 23, type = 15),
                Maneuver("Arrive.", 0.0, 0.0, 40, type = 4),
            ),
            2.2, 120.0,
        )
        val p = RouteTracker(route).update(shape[10])
        assertEquals(10, p.upcoming?.type)
        assertEquals(15, p.following?.type)
        assertEquals(3 * 55.6, p.followingGapMeters!!, 2.0)

        val last = RouteTracker(route, resumed = true).update(shape[30]) // resumed: may start anywhere
        assertEquals(4, last.upcoming?.type)
        assertNull(last.following)
    }
}
