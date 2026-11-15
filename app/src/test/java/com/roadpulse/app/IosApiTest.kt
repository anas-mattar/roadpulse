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
                    path == "/v1/me" && request.method == "DELETE" -> MockResponse().setResponseCode(204)
                    path == "/v1/me" -> MockResponse().setBody(
                        """{"nickname":"Anas","points":130,"level":"ROAD_SCOUT","levelIndex":1,"nextLevelPoints":500,
                            "stats":{"reports":12,"confirmationsReceived":5,"votes":3},"badges":["FIRST_REPORT","REPORTER_10"]}""")
                    path == "/v1/me/export" -> MockResponse().setBody("""{"reports":[]}""")
                    path.startsWith("/api/?") -> MockResponse().setBody(
                        """{"features":[{"geometry":{"coordinates":[-90.88,32.35]},"properties":{"name":"MCITy","country":"United States"}},
                            {"geometry":{"coordinates":[101.72,3.16]},"properties":{"name":"M City","street":"Jalan Ampang","country":"Malaysia"}}]}""")
                    path.startsWith("/v1/places/search") -> MockResponse().setBody(
                        """[{"id":"o1","name":"M City Dental Clinic","category":"dental_clinic","address":"Jalan Ampang","lat":3.1602,"lon":101.7202,"distanceM":60},
                            {"id":"o2","name":"M City","category":"shopping_mall","lat":3.16,"lon":101.72,"distanceM":0}]""")
                    path.startsWith("/v1/places/community/search") -> MockResponse().setBody(
                        """[{"id":"c1","name":"M City Lobby 2","kind":"ENTRANCE","note":"Side gate","lat":3.1601,"lon":101.7201,"distanceM":40,"status":"Approved","own":false}]""")
                    path == "/v1/places/community" -> MockResponse().setResponseCode(201).setBody(
                        """{"id":"c2","name":"Klinik Sara","kind":"CLINIC","lat":3.1,"lon":101.6,"status":"Pending","own":true}""")
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

        val mine = api.report("POLICE", 3.14, 101.69, "HIDDEN", -1, GuidanceLanguage.AR)!!
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
    fun reportDetailsProfileAndPrivacy() = runBlocking {
        val s = server()
        val base = s.url("").toString().trimEnd('/')
        val api = RoadPulseApi("http://unused", "http://unused", base)

        assertEquals(listOf("VISIBLE", "Visible"), api.reportDetails("POLICE", GuidanceLanguage.EN).first())
        assertTrue(api.reportDetails("ROAD_CLOSED", GuidanceLanguage.EN).isNotEmpty())
        assertEquals(listOf("60", "1 hour"), api.closureDurations(GuidanceLanguage.EN)[1])
        assertEquals("", api.closureDurations(GuidanceLanguage.EN)[0][0])

        api.report("ROAD_CLOSED", 3.14, 101.69, "FULL", 180, GuidanceLanguage.EN)
        var sent: RecordedRequest
        do { sent = s.takeRequest() } while (sent.path != "/v1/reports")
        val body = sent.body.readUtf8()
        assertTrue(body, body.contains("\"subtype\":\"FULL\"") && body.contains("180"))

        val card = api.profile(GuidanceLanguage.EN)!!
        assertEquals("Anas", card.nickname)
        assertEquals(12, card.reports)
        assertEquals(2, card.badges.count { it.earned })
        assertEquals(5, card.badges.size)
        assertTrue(card.progress > 0f && card.progress < 1f)

        assertEquals("""{"reports":[]}""", api.exportMyData())
        api.deleteMyData()
        s.shutdown()
    }

    @Test
    fun searchMixesDriversPlacesWithOpenStreetMapNearestFirst() = runBlocking {
        val s = server()
        val base = s.url("").toString().trimEnd('/')
        val api = RoadPulseApi(base, "http://unused", base)
        val hits = api.search("mcity", 3.16, 101.72, GuidanceLanguage.EN)
        // Drivers' place, then Overture places, then OpenStreetMap; "M City" from both sources once; the USA last.
        assertEquals(listOf("M City Lobby 2", "M City Dental Clinic", "M City", "MCITy"), hits.map { it.name })
        assertEquals("Entrance · Added by drivers · Side gate", hits[0].detail)
        assertEquals("Dental clinic · Jalan Ampang", hits[1].detail)
        assertTrue(api.addPlace("Klinik Sara", "CLINIC", 3.1, 101.6, ""))
        assertEquals("CLINIC", api.placeKinds(GuidanceLanguage.EN)[3][0])
        s.shutdown()
    }

    @Test
    fun savedPlacesAndPlannedDrivesOnThePhone() {
        val book = com.roadpulse.app.ios.SavedPlacesBook()
        book.clear()
        val klcc = com.roadpulse.app.ios.SearchResult("KLCC", "Kuala Lumpur", 3.158, 101.712)
        book.setHome(klcc)
        book.addRecent(klcc)
        book.addRecent(klcc)
        book.addFavourite("Gym", com.roadpulse.app.ios.SearchResult("Fitness", "", 3.1, 101.6))
        assertEquals("KLCC", book.home!!.name)
        assertEquals(1, book.recent.size)
        assertEquals("Gym", book.favourites.single().name)

        val route = com.roadpulse.app.ios.RouteSummary("Fastest", null, 30, 20.0, false, emptyList(),
            com.roadpulse.app.data.Route(emptyList(), emptyList(), 20.0, 1800.0), null)
        val now = 1_800_000_000_000L
        assertNull(book.addPlanned(klcc, route, now + 20 * 60_000L, now)) // 30 min drive: too late
        val planned = book.addPlanned(klcc, route, now + 3 * 3_600_000L, now)!!
        assertEquals(now + 3 * 3_600_000L - 40 * 60_000L, planned.leaveAtMillis) // drive + 10 spare
        assertEquals(1, book.planned(now).size)
        book.setParking(3.1, 101.6, now - 2 * 3_600_000L)
        assertEquals("Your car · parked 2 h ago", book.parkingLabel(now, GuidanceLanguage.EN))
        book.clearParking()
        assertNull(book.parking)
        book.removePlanned(planned.id)
        assertTrue(book.planned(now).isEmpty())
    }

    @Test
    fun withoutAServerTheFeaturesAreQuiet() = runBlocking {
        val api = RoadPulseApi("http://unused", "http://unused")
        assertTrue(!api.serverReachable())
        assertTrue(api.reportsIn(3.1, 101.6, 3.2, 101.7, GuidanceLanguage.EN).isEmpty())
        assertNull(api.report("POLICE", 3.1, 101.6, null, -1, GuidanceLanguage.EN))
        assertNull(api.profile(GuidanceLanguage.EN))
    }

    @Test
    fun isoTimes() {
        assertEquals(1_794_661_212_772L, IsoTime.parseMillis("2026-11-14T13:00:12.772446+00:00"))
        assertEquals(1_794_661_212_000L, IsoTime.parseMillis("2026-11-14T21:00:12+08:00"))
        assertEquals(0L, IsoTime.parseMillis("1970-01-01T00:00:00Z"))
    }
}
