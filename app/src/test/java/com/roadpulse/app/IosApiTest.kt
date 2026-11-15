package com.roadpulse.app

import com.roadpulse.app.data.IsoTime
import com.roadpulse.app.data.ReportFeed
import com.roadpulse.app.ios.RoadPulseApi
import com.roadpulse.app.nav.GuidanceLanguage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iPhone app's server features (reports, places, traffic, tolls), checked on the JVM. */
class IosApiTest {
    private val report = """{"id":"r1","type":"POLICE","lat":3.14,"lon":101.69,"createdAt":"2026-11-15T08:00:00+00:00",
        "expiresAt":"2026-11-15T09:00:00.5+00:00","confirmations":2,"dismissals":0,"subtype":"HIDDEN"}"""

    private fun server(): MockWebServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                return when {
                    path == "/health" -> MockResponse().setBody("""{"status":"ok"}""")
                    path == "/v1/devices" -> MockResponse().setResponseCode(201).setBody("""{"deviceId":"d","token":"t1"}""")
                    path.startsWith("/v1/reports?bbox=") -> MockResponse().setBody("[$report]")
                    path == "/v1/reports" -> MockResponse().setResponseCode(201).setBody("""{"report":$report,"merged":false}""")
                    path.endsWith("/votes") -> MockResponse().setBody(report)
                    path.startsWith("/v1/places/nearby") -> MockResponse().setBody(
                        """[{"id":"n1","category":"FUEL","name":"Petronas","lat":3.15,"lon":101.7,"distanceM":240,"openingHours":"Mo-Su 06:00-23:00"}]""")
                    path.startsWith("/v1/traffic?") -> MockResponse().setBody(
                        """[{"edgeId":1,"level":"JAM","speedKph":8,"expectedKph":60,"points":[[3.1,101.6],[3.1,101.61]]},
                            {"edgeId":2,"level":"FREE","speedKph":60,"expectedKph":60,"points":[[3.2,101.6],[3.2,101.61]]}]""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }

    @Test
    fun reportsPlacesAndTrafficFromTheLaptopServer() = runBlocking {
        val s = server()
        val base = s.url("").toString().trimEnd('/')
        val api = RoadPulseApi("http://unused", "http://unused", base)
        assertTrue(api.serverReachable())

        val pins = api.reportsIn(3.1, 101.6, 3.2, 101.7, GuidanceLanguage.EN)
        assertEquals("Police", pins.single().label)
        assertEquals("Hidden · 2 confirmed", pins.single().detail)
        assertEquals(1, ReportFeed.reports.value.size) // shared with the drive for warnings ahead

        val mine = api.report("POLICE", 3.14, 101.69, null, GuidanceLanguage.AR)!!
        assertEquals("شرطة", mine.label)
        assertTrue(mine.own)
        api.vote("r1", true)

        val fuel = api.places("FUEL", 3.14, 101.69, GuidanceLanguage.EN, dayIndex = 4, hour = 10, minute = 0).single()
        assertEquals("Petronas", fuel.name)
        assertEquals("240 m · Open · closes 23:00", fuel.detail)

        val traffic = api.traffic(3.0, 101.5, 3.3, 101.8)
        assertEquals("JAM", traffic.single().level) // free-flowing roads are not drawn
        assertEquals(listOf("FUEL", "Fuel"), api.categories(GuidanceLanguage.EN).first())
        s.shutdown()
    }

    @Test
    fun withoutAServerTheFeaturesAreQuiet() = runBlocking {
        val api = RoadPulseApi("http://unused", "http://unused")
        assertTrue(!api.serverReachable())
        assertTrue(api.reportsIn(3.1, 101.6, 3.2, 101.7, GuidanceLanguage.EN).isEmpty())
        assertNull(api.report("POLICE", 3.1, 101.6, null, GuidanceLanguage.EN))
    }

    @Test
    fun isoTimes() {
        assertEquals(1_794_661_212_772L, IsoTime.parseMillis("2026-11-14T13:00:12.772446+00:00"))
        assertEquals(1_794_661_212_000L, IsoTime.parseMillis("2026-11-14T21:00:12+08:00"))
        assertEquals(0L, IsoTime.parseMillis("1970-01-01T00:00:00Z"))
    }
}
