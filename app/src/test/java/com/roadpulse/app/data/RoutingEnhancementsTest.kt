package com.roadpulse.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Planning around reported closures, the time of day, and starting in the direction of travel. */
class RoutingEnhancementsTest {
    private val original = Http.transport
    private val klcc = GeoPoint(3.1579, 101.7116)
    private val gurney = GeoPoint(3.1720, 101.7150)
    private val closure = GeoPoint(3.1650, 101.7130)

    @Before fun clean() { Closures.forget(); TravelHeading.forget() }
    @After fun restore() { Http.transport = original; Closures.forget(); TravelHeading.forget() }

    private fun report(type: String, p: GeoPoint) =
        """{"id":"r","type":"$type","lat":${p.lat},"lon":${p.lon},"createdAt":"2026-11-17T01:00:00Z","expiresAt":"2026-11-17T05:00:00Z"}"""

    @Test
    fun oneAreaForATownTripTwoForALongOne() {
        val town = Closures.areas(klcc, gurney).single()
        assertTrue(town.contains(klcc) && town.contains(gurney) && town.contains(closure))
        val penang = GeoPoint(5.41, 100.33)
        val far = Closures.areas(klcc, penang)
        assertEquals(2, far.size)
        assertTrue(far[0].contains(klcc) && far[1].contains(penang))
        assertTrue(far.all { it.width <= Closures.MAX_BOX_DEG && it.height <= Closures.MAX_BOX_DEG })
    }

    @Test
    fun closuresNextToTheStartOrDestinationAreIgnored() {
        val atStart = GeoPoint(klcc.lat + 0.0005, klcc.lon)      // about 55 m from the start
        val atEnd = GeoPoint(gurney.lat, gurney.lon + 0.0008)    // about 90 m from the destination
        assertEquals(listOf(closure), Closures.usable(listOf(atStart, closure, atEnd, closure), klcc, listOf(gurney)))
    }

    @Test
    fun readsOnlyRoadClosuresAndRemembersThem() = runBlocking {
        var calls = 0
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult {
                calls++
                return HttpResult(200, "[${report("ROAD_CLOSED", closure)},${report("POLICE", GeoPoint(3.16, 101.712))}]")
            }
        }
        assertEquals(listOf(closure), Closures.around("https://server.example", klcc, gurney))
        assertEquals(listOf(closure), Closures.around("https://server.example", klcc, gurney)) // from memory
        assertEquals(1, calls)
        assertEquals(emptyList<GeoPoint>(), Closures.around("", klcc, gurney)) // no server: nothing to avoid
    }

    @Test
    fun aSlowOrBrokenServerNeverHoldsUpRouting() = runBlocking {
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult { delay(5_000); return HttpResult(200, "[]") }
        }
        val start = System.currentTimeMillis()
        assertEquals(emptyList<GeoPoint>(), Closures.around("https://server.example", klcc, gurney, budgetMs = 200))
        assertTrue(System.currentTimeMillis() - start < 2_000)
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult = throw IOException("down")
        }
        assertEquals(emptyList<GeoPoint>(), Closures.around("https://server.example", klcc, gurney))
    }

    @Test
    fun requestLeavesOutClosedRoadsAndSendsTheTimeOnlyWithoutAlternatives() {
        val client = RoutingClient("https://routing.example")
        val single = client.requestBody(klcc, gurney, closures = listOf(closure))
        assertTrue(single, single.contains("\"exclude_locations\":[{\"lat\":3.165,\"lon\":101.713}]"))
        assertTrue(single, single.contains("\"date_time\":{\"type\":0}"))
        // With alternatives the routing server cannot use the time, so it is not sent.
        val choices = client.requestBody(klcc, gurney, alternates = 2)
        assertFalse(choices, choices.contains("date_time") || choices.contains("exclude_locations"))
        // A trip with stops has no alternatives anyway: the time is sent.
        assertTrue(client.requestBody(klcc, gurney, stops = listOf(closure), alternates = 2).contains("date_time"))
    }

    @Test
    fun plansWithoutTheClosuresWhenNoRouteGoesAroundThem() = runBlocking {
        val bodies = mutableListOf<String>()
        val ok = """{"trip":{"legs":[{"shape":"","maneuvers":[],"summary":{"length":1.0,"time":60.0}}],"summary":{"length":1.0,"time":60.0}}}"""
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult {
                bodies += body.orEmpty()
                return if (body.orEmpty().contains("exclude_locations")) HttpResult(400, """{"error_code":442,"error":"No path could be found for input"}""")
                else HttpResult(200, ok)
            }
        }
        val route = runCatching { RoutingClient("https://routing.example").route(klcc, gurney, closures = listOf(closure)) }
        assertEquals(2, bodies.size)
        assertTrue(bodies[0].contains("exclude_locations"))
        assertFalse(bodies[1].contains("exclude_locations"))
        assertTrue(route.exceptionOrNull()?.toString().orEmpty(), route.isSuccess)
    }

    @Test
    fun headingIsUsedOnlyWhileMovingRecentlyAndNearby() {
        assertNull(TravelHeading.from(klcc))
        TravelHeading.update(klcc.lat, klcc.lon, 90.0, 10.0)
        assertEquals(90.0, TravelHeading.from(klcc)!!, 0.0)
        assertNull(TravelHeading.from(gurney))                                           // a route from somewhere else
        assertNull(TravelHeading.from(klcc, System.currentTimeMillis() + 60_000))        // too old
        TravelHeading.update(klcc.lat, klcc.lon, 90.0, 0.5)                              // standing still: heading unreliable
        assertNull(TravelHeading.from(klcc))
        TravelHeading.update(klcc.lat, klcc.lon, -1.0, -1.0)                             // iPhone: not known
        assertNull(TravelHeading.from(klcc))
    }
}
