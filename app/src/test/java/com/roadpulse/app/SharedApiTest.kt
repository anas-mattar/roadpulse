package com.roadpulse.app

import com.roadpulse.app.data.Http
import com.roadpulse.app.ios.RoadPulseApi
import com.roadpulse.app.nav.GuidanceLanguage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The facade the iPhone app calls, exercised here on the JVM against a fake server. */
class SharedApiTest {
    @Test
    fun searchAndRoutesThroughTheSharedClients() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """{"features":[{"geometry":{"coordinates":[101.7116,3.1579]},"properties":{"name":"M City","city":"Kuala Lumpur","country":"Malaysia"}}]}"""
        ))
        server.enqueue(MockResponse().setBody(javaClass.classLoader!!.getResource("valhalla_route_with_lanes.json")!!.readText()))
        server.start()
        val base = server.url("").toString().trimEnd('/')
        val api = RoadPulseApi(base, base)

        val hit = api.search("M City Jalan Ampang", 3.139, 101.6869).single()
        assertEquals("M City", hit.name)
        assertEquals("Kuala Lumpur, Malaysia", hit.detail)
        val searchPath = server.takeRequest().path!!
        assertTrue(searchPath, searchPath.startsWith("/api/?q=M%20City%20Jalan%20Ampang&limit=8&lat=3.139&lon=101.6869"))

        val routes = api.routes(3.139, 101.6869, hit.lat, hit.lon, GuidanceLanguage.AR)
        assertEquals("الأسرع", routes.single().tag)
        assertTrue(routes.single().minutes > 5)
        val routeRequest = server.takeRequest()
        assertEquals("RoadPulse/0.1", routeRequest.getHeader("User-Agent"))
        assertTrue(routeRequest.getHeader("Content-Type")!!.startsWith("application/json"))
        server.shutdown()
    }

    @Test
    fun queryValuesArePercentEncoded() {
        assertEquals("a%20b%26c%3D%D8%B9", Http.encode("a b&c=ع"))
        assertEquals("http://x/y?q=1%2C2&z=ok", Http.url("http://x/y", "q" to "1,2", "z" to "ok"))
    }
}
