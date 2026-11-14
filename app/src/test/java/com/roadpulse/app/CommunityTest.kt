package com.roadpulse.app

import com.roadpulse.app.data.DeviceApi
import com.roadpulse.app.data.DeviceTokenStore
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Profile
import com.roadpulse.app.data.ProfileClient
import com.roadpulse.app.data.ShareClient
import com.roadpulse.app.data.TrafficClient
import com.roadpulse.app.nav.TrafficSegmenter
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityTest {
    private class MemTokens(var value: String? = null) : DeviceTokenStore {
        override fun load() = value
        override fun save(token: String?) { value = token }
    }

    /** A drive east along lat 3.1, one fix per second at ~14 m/s (about 50 km/h). */
    private fun drive(seg: TrafficSegmenter, seconds: Int, totalM: Double = 10_000.0): List<List<com.roadpulse.app.data.TrafficPoint>> {
        val out = mutableListOf<List<com.roadpulse.app.data.TrafficPoint>>()
        for (s in 0..seconds) {
            val metres = s * 14.0
            val p = GeoPoint(3.1, 101.6 + metres / 111_000.0)
            seg.add(p, 14.0, s * 1000L, totalM - metres)?.let(out::add)
        }
        return out
    }

    @Test
    fun trafficSegmentsSkipTheStartAndEndOfTheTrip() {
        val segments = drive(TrafficSegmenter(), seconds = 700, totalM = 10_000.0) // 9.8 km
        assertTrue(segments.isNotEmpty())
        val all = segments.flatten()
        val startLon = 101.6
        // Nothing from the first 500 m.
        assertTrue(all.all { (it.lon - startLon) * 111_000 >= 495 })
        // Nothing from the last 500 m before the destination.
        assertTrue(all.all { (it.lon - startLon) * 111_000 <= 9_500 + 5 })
        segments.forEach { seg ->
            assertTrue(seg.size in 5..61)
            assertEquals(0, seg.first().t)          // times relative to the segment start
            assertTrue(seg.last().t <= 120)
        }
    }

    @Test
    fun aShortTripSendsNothing() {
        assertTrue(drive(TrafficSegmenter(), seconds = 70, totalM = 1_000.0).isEmpty())
    }

    @Test
    fun profileProgress() {
        assertEquals(0.5f, Profile(points = 300, levelIndex = 1, nextLevelPoints = 500).progress(), 0.001f)
        assertEquals(1f, Profile(points = 12_000, levelIndex = 4, nextLevelPoints = null).progress(), 0.001f)
    }

    @Test
    fun clientsRegisterOnceAndCallTheServer() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"deviceId":"d1","token":"tok"}"""))
        server.enqueue(MockResponse().setBody("""{"nickname":"Ahmad","points":15,"level":"LEARNER","levelIndex":0,"nextLevelPoints":100,
            "stats":{"reports":1,"confirmationsReceived":1,"votes":0,"removed":0},"badges":["FIRST_REPORT"]}"""))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"abc","url":"http://localhost:8080/s/abc","expiresAt":"2026-11-15T20:00:00Z"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setBody("""[{"edgeId":1,"level":"JAM","speedKph":11,"expectedKph":60,"points":[[3.1,101.6],[3.1,101.61]]}]"""))
        server.start()
        val base = server.url("").toString().trimEnd('/')
        val tokens = MemTokens()
        val api = DeviceApi(base, tokens)

        val profile = ProfileClient(api).get()
        assertEquals("Ahmad", profile.nickname)
        assertEquals("tok", tokens.value)
        assertEquals("/v1/devices", server.takeRequest().path)
        val me = server.takeRequest()
        assertEquals("Bearer tok", me.getHeader("Authorization"))

        val share = ShareClient(api)
        val link = share.start(Place("M City", "", GeoPoint(3.15, 101.71)), GeoPoint(3.1, 101.6), 1_800_000_000_000, "Ahmad")
        assertEquals("http://localhost:8080/s/abc", link.url)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"destination\":\"M City\""))
        share.update("abc", GeoPoint(3.12, 101.65), 1_800_000_000_000, "DRIVING")
        assertEquals("PUT", server.takeRequest().method)

        val traffic = TrafficClient(base)
        traffic.upload(listOf(com.roadpulse.app.data.TrafficPoint(3.1, 101.6, 0, 10.0)))
        val upload = server.takeRequest()
        assertNull(upload.getHeader("Authorization")) // anonymous by design
        assertFalse(upload.body.readUtf8().contains("device"))
        val lines = traffic.lines(3.0, 101.5, 3.2, 101.7)
        assertEquals("JAM", lines.single().level)
        assertNotNull(server.takeRequest().path?.contains("minLat=3.0"))
        server.shutdown()
    }
}
