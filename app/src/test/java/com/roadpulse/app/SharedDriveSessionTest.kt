package com.roadpulse.app

import com.roadpulse.app.data.Place
import com.roadpulse.app.data.RouteOptions
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.ArabicNarrator
import com.roadpulse.app.nav.DrivePlatform
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.NavEvent
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.NavigationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The drive session that Android and iPhone share, driven here without any phone: a real route
 * (KL Sentral to Sunway Pyramid) is followed point by point with a fake platform.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedDriveSessionTest {
    private object ArabicNoServers : DrivePlatform {
        override fun narrator(): Narrator = ArabicNarrator
        override fun routingUrl() = ""
        override fun backendUrl() = ""
        override fun routeOptions() = RouteOptions()
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() {
        NavigationSession.stop()
        NavigationSession.platform = DrivePlatform.Default
        Dispatchers.resetMain()
    }

    @Test
    fun followsARouteToArrivalWithPromptsInTheChosenLanguage() = runTest(UnconfinedTestDispatcher()) {
        val json = javaClass.classLoader!!.getResource("valhalla_route_with_lanes.json")!!.readText()
        val route = RoutingClient("http://unused").parseRoute(json)
        val destination = Place("Sunway Pyramid", "", route.shape.last())
        val events = mutableListOf<NavEvent>()
        val collecting: Job = launch { NavigationSession.events.collect { events += it } }

        NavigationSession.platform = ArabicNoServers
        var clock = 0L
        NavigationSession.clock = { clock }
        NavigationSession.start(route, destination, persistDrive = false)
        assertNotNull(NavigationSession.state.value)

        var sawLanes = false
        for (p in route.shape) {
            clock += 1_000
            NavigationSession.onLocation(LocationFix(p, accuracyM = 5.0, speedMps = 14.0, timeMillis = clock))
            val s = NavigationSession.state.value ?: break
            if (s.lanes.isNotEmpty()) sawLanes = true
        }
        collecting.cancel()

        assertNull("the drive ends on arrival", NavigationSession.state.value)
        assertTrue(events.any { it is NavEvent.Arrived })
        val spoken = events.filterIsInstance<NavEvent.Speak>().map { it.text }
        assertTrue("several prompts were spoken: $spoken", spoken.size >= 5)
        assertEquals(ArabicNarrator.arrived, spoken.last())
        assertTrue(spoken.any { it.contains("بعد") })   // "In … metres," advance warnings in Arabic
        assertTrue(sawLanes)
    }

    /** The controller the iPhone app uses: a simulated drive of the same route, on virtual time. */
    @Test
    fun iPhoneControllerSimulatesADriveToArrival() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(javaClass.classLoader!!.getResource("valhalla_route_with_lanes.json")!!.readText()))
        server.start()
        val base = server.url("").toString().trimEnd('/')
        val route = com.roadpulse.app.ios.RoadPulseApi(base, base)
            .routes(3.139, 101.6869, 3.0738, 101.607, emptyList(), com.roadpulse.app.data.RouteOptions(), com.roadpulse.app.nav.GuidanceLanguage.EN).single()
        server.shutdown()

        NavigationSession.clock = { testScheduler.currentTime }
        val drive = com.roadpulse.app.ios.DriveController("")
        drive.language = com.roadpulse.app.nav.GuidanceLanguage.MS
        val spoken = mutableListOf<String>()
        val snapshots = mutableListOf<com.roadpulse.app.ios.DriveSnapshot>()
        var arrived = false
        drive.observe(onState = { if (it != null) snapshots += it }, onSpeak = { spoken += it }, onArrived = { arrived = true })
        drive.startSimulated(route, "Sunway Pyramid", 3.0738, 101.607, speedKmh = 60.0)
        testScheduler.advanceTimeBy(40 * 60_000L)

        assertTrue("arrived", arrived)
        assertTrue(!drive.isDriving)
        assertTrue("Malay prompts: $spoken", spoken.any { it.startsWith("Dalam ") })
        assertTrue(snapshots.size > 100)
        assertTrue(snapshots.any { it.lanes.isNotEmpty() })
        assertTrue(snapshots.any { it.bearingDeg >= 0 && it.hasPosition })
        assertTrue(snapshots.first().remainingKm > snapshots.last().remainingKm)
        assertEquals(60, snapshots[50].speedKmh)
    }
}
