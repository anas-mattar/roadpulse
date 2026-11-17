package com.roadpulse.app

import com.roadpulse.app.data.Closures
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Http
import com.roadpulse.app.data.HttpResult
import com.roadpulse.app.data.HttpTransport
import com.roadpulse.app.data.IOException
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Polyline
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RouteOptions
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.DrivePlatform
import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.NavEvent
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.NavigationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** During a drive: a road ahead is reported as closed, and a route around it is offered once. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClosureAheadTest {
    private object WithServers : DrivePlatform {
        override fun narrator(): Narrator = EnglishNarrator
        override fun routingUrl() = "https://routing.example"
        override fun backendUrl() = "https://server.example"
        override fun routeOptions() = RouteOptions()
    }

    private val original = Http.transport
    private lateinit var route: Route
    private lateinit var closure: GeoPoint
    /** What the routing server answers to the detour request: set by each test. */
    private var detourShape: List<GeoPoint> = emptyList()
    private var routeRequests = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Closures.forget()
        val json = javaClass.classLoader!!.getResource("valhalla_route_with_lanes.json")!!.readText()
        route = RoutingClient("http://unused").parseRoute(json)
        closure = route.shape[route.shape.size / 2]
        routeRequests = mutableListOf()
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult = when {
                url.startsWith("https://server.example/v1/reports") -> HttpResult(200,
                    """[{"id":"c1","type":"ROAD_CLOSED","lat":${closure.lat},"lon":${closure.lon},"createdAt":"2026-11-17T01:00:00Z","expiresAt":"2026-11-17T05:00:00Z"}]""")
                url == "https://routing.example/route" -> {
                    routeRequests += body.orEmpty()
                    HttpResult(200, """{"trip":{"legs":[{"shape":"${Polyline.encode(detourShape).replace("\\", "\\\\")}","maneuvers":[],"summary":{"length":20.0,"time":1500.0}}],"summary":{"length":20.0,"time":1500.0}}}""")
                }
                else -> throw IOException("not part of this test: $url")
            }
        }
    }

    @After
    fun tearDown() {
        NavigationSession.stop()
        NavigationSession.platform = DrivePlatform.Default
        Http.transport = original
        Closures.forget()
        Dispatchers.resetMain()
    }

    /** Ends the drive when the test body ends (also on a failed check): its periodic checks would otherwise keep the test's virtual clock busy forever. */
    private suspend fun driving(body: suspend () -> Unit) {
        try { body() } finally { NavigationSession.stop() }
    }

    /** Starts the drive and moves the car over the first few points of the route. */
    private fun startDriving() {
        NavigationSession.platform = WithServers
        var clock = 0L
        NavigationSession.clock = { clock }
        NavigationSession.start(route, Place("Sunway Pyramid", "", route.shape.last()), persistDrive = false)
        for (p in route.shape.take(4)) {
            clock += 1_000
            NavigationSession.onLocation(LocationFix(p, accuracyM = 5.0, speedMps = 14.0, timeMillis = clock))
        }
    }

    @Test
    fun offersARouteAroundAClosureAheadOnce() = runTest(UnconfinedTestDispatcher()) { driving {
        val spoken = mutableListOf<String>()
        val collecting = launch { NavigationSession.events.collect { if (it is NavEvent.Speak) spoken += it.text } }
        startDriving()
        // The detour leaves the route well away from the closed road.
        val start = route.shape[3]
        detourShape = listOf(start, GeoPoint(start.lat + 0.03, start.lon + 0.03), route.shape.last())

        NavigationSession.checkClosures()
        val offer = NavigationSession.state.value?.fasterRoute
        assertNotNull("a route around the closure is offered", offer)
        assertTrue(offer!!.closureAhead)
        assertTrue("the routing server was told about the closed road", routeRequests.single().contains("exclude_locations"))
        assertTrue(spoken.toString(), spoken.contains(EnglishNarrator.closureAheadNewRoute))

        // Keeping the route: the same closure is not offered again.
        NavigationSession.declineFasterRoute()
        NavigationSession.checkClosures()
        assertNull(NavigationSession.state.value?.fasterRoute)
        assertEquals(1, routeRequests.size)
        collecting.cancel()
    } }

    @Test
    fun takingTheOfferSwitchesToTheNewRoute() = runTest(UnconfinedTestDispatcher()) { driving {
        startDriving()
        val start = route.shape[3]
        detourShape = listOf(start, GeoPoint(start.lat + 0.03, start.lon + 0.03), route.shape.last())
        NavigationSession.checkClosures()
        NavigationSession.acceptFasterRoute()
        val now = NavigationSession.state.value!!
        assertNull(now.fasterRoute)
        assertEquals(detourShape.size, now.route.shape.size)
    } }

    @Test
    fun offersNothingWhenNoRouteGoesAroundTheClosure() = runTest(UnconfinedTestDispatcher()) { driving {
        startDriving()
        detourShape = route.shape // the routing server found only the same way, through the closed road
        NavigationSession.checkClosures()
        assertNull(NavigationSession.state.value?.fasterRoute)
        // And it does not keep asking the routing server about it.
        NavigationSession.checkClosures()
        assertEquals(1, routeRequests.size)
    } }
}
