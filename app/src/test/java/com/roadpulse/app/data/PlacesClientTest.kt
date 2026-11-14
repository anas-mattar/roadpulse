package com.roadpulse.app.data

import com.roadpulse.app.nav.EnglishNarrator
import com.roadpulse.app.nav.MalayNarrator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PlacesClientTest {
    @Test
    fun nearbyAskesForTheCategoryAndFormatsResults() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """[{"id":"n1","category":"PARKING","name":"Pavilion Car Park","lat":3.149,"lon":101.713,"paid":true,"distanceM":240},
                {"id":"n2","category":"PARKING","brand":"Wilson Parking","lat":3.15,"lon":101.71,"paid":false,"distanceM":1530}]"""
        ))
        server.start()
        val found = PlacesClient(server.url("").toString().trimEnd('/')).nearby(PlaceCategory.PARKING, GeoPoint(3.147, 101.711), radiusM = 600, limit = 5)
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/v1/places/nearby?category=PARKING&lat=3.147&lon=101.711&radiusM=600&limit=5"))
        assertEquals(Place("Pavilion Car Park", "240 m · paid", GeoPoint(3.149, 101.713)), found[0].toPlace())
        assertEquals("Wilson Parking", found[1].toPlace().name)
        assertEquals("1.5 km · free", found[1].toPlace().description)
        server.shutdown()
    }

    @Test
    fun alongRouteSendsTheRouteAsLatLonPoints() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""[{"id":"n9","category":"SPEED_CAMERA","lat":3.0,"lon":101.6,"maxSpeedKph":90}]"""))
        server.start()
        val cams = PlacesClient(server.url("").toString().trimEnd('/'))
            .alongRoute(PlaceCategory.SPEED_CAMERA, listOf(GeoPoint(3.0, 101.5), GeoPoint(3.0, 101.7)))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("SPEED_CAMERA", body["category"]!!.jsonPrimitive.content)
        assertEquals(101.7, body["points"]!!.jsonArray[1].jsonArray[1].jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(90, cams.single().maxSpeedKph)
        server.shutdown()
    }

    @Test
    fun longRoutesAreThinnedButKeepBothEnds() {
        val shape = (0 until 10_001).map { GeoPoint(3.0, 101.0 + it * 0.00001) }
        val thin = PlacesClient.thin(shape, PlacesClient.MAX_POINTS)
        assertTrue(thin.size <= PlacesClient.MAX_POINTS + 1)
        assertEquals(shape.first(), thin.first())
        assertEquals(shape.last(), thin.last())
    }

    @Test
    fun cameraWarningsInBothLanguages() {
        assertEquals("Speed camera in 500 meters. Limit 90.", EnglishNarrator.cameraAhead(500, 90))
        assertEquals("Speed camera in 300 meters.", EnglishNarrator.cameraAhead(300, null))
        assertEquals("Kamera had laju dalam 500 meter. Had laju 90.", MalayNarrator.cameraAhead(500, 90))
    }

    /** Against a running backend loaded with places (skipped unless ROADPULSE_PLACES_LAT/LON are set). */
    @Test
    fun nearbyAgainstARealBackend() = runBlocking {
        val url = System.getenv("ROADPULSE_BACKEND_URL")
        val lat = System.getenv("ROADPULSE_PLACES_LAT")?.toDoubleOrNull()
        val lon = System.getenv("ROADPULSE_PLACES_LON")?.toDoubleOrNull()
        assumeTrue(url != null && lat != null && lon != null)
        val fuel = PlacesClient(url!!).nearby(PlaceCategory.FUEL, GeoPoint(lat!!, lon!!), radiusM = 3_000)
        assertTrue("no fuel stations returned", fuel.isNotEmpty())
        assertEquals(fuel.sortedBy { it.distanceM }, fuel)
    }
}
