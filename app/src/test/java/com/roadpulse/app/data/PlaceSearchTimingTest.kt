package com.roadpulse.app.data

import com.roadpulse.app.i18n.EnglishStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A slow search service must not hold up the list (search-while-typing). */
class PlaceSearchTimingTest {
    private val original = Http.transport

    @After
    fun restore() { Http.transport = original }

    private val here = GeoPoint(3.158, 101.712)
    private val serverHit = """[{"id":"a1","name":"M City","lat":3.159,"lon":101.747,"distanceM":3900}]"""
    private val osmHit = """{"features":[{"geometry":{"coordinates":[101.70,3.15]},"properties":{"name":"KLCC Park"}}]}"""

    /** Pretend servers: the place server and the OpenStreetMap search each answer after their own delay. */
    private fun servers(serverMs: Long, osmMs: Long, serverBody: String = serverHit) {
        Http.transport = object : HttpTransport {
            override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult =
                if (url.startsWith("https://server.example")) { delay(serverMs); HttpResult(200, serverBody) }
                else { delay(osmMs); HttpResult(200, osmHit) }
        }
    }

    private suspend fun search(serverBudget: Long = 300, osmGrace: Long = 200) = PlaceSearch.search(
        GeocodingClient("https://osm.example"), null, "mcity", here, EnglishStrings, 10,
        DirectoryClient("https://server.example"), serverBudget, osmGrace)

    @Test
    fun slowOpenStreetMapSearchIsLeftOutWhenTheServerFoundPlaces() = runBlocking {
        servers(serverMs = 10, osmMs = 5_000)
        val start = System.currentTimeMillis()
        val names = search().map { it.name }
        assertEquals(listOf("M City"), names)
        assertTrue("took ${System.currentTimeMillis() - start} ms", System.currentTimeMillis() - start < 2_000)
    }

    @Test
    fun slowServerIsLeftOutAndOpenStreetMapStillAnswers() = runBlocking {
        servers(serverMs = 5_000, osmMs = 10)
        val start = System.currentTimeMillis()
        assertEquals(listOf("KLCC Park"), search().map { it.name })
        assertTrue(System.currentTimeMillis() - start < 2_000)
    }

    @Test
    fun bothAnswerInTime() = runBlocking {
        servers(serverMs = 10, osmMs = 50)
        assertEquals(setOf("M City", "KLCC Park"), search().map { it.name }.toSet())
    }
}
