package com.roadpulse.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Collections

/** Server-backed reports against a fake HTTP server: auth, errors and expiry. */
class BackendReportRepositoryTest {
    private val server = MockWebServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var registrations = 0
    private val validTokens = mutableSetOf<String>()
    private var clock = 1_794_660_000_000L // 2026-11-14 around 12:00 UTC
    private var voteResponse: MockResponse? = null

    private class MemoryTokens(var token: String? = null) : DeviceTokenStore {
        override fun load() = token
        override fun save(token: String?) { this.token = token }
    }

    private fun dto(id: String, expires: String = "2026-11-14T14:00:00+00:00") =
        """{"id":"$id","type":"POLICE","lat":3.1,"lon":101.6,"createdAt":"2026-11-14T12:00:00+00:00","expiresAt":"$expires","confirmations":0,"dismissals":0}"""

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.path!!
                val auth = request.getHeader("Authorization")?.removePrefix("Bearer ")
                return when {
                    path == "/v1/devices" -> {
                        registrations++
                        val token = "token-$registrations-abcdefghijklmnopqrstuvwxyz"
                        validTokens += token
                        MockResponse().setResponseCode(201).setBody("""{"deviceId":"d$registrations","token":"$token"}""")
                    }
                    path.startsWith("/v1/reports?bbox=") -> MockResponse().setBody(
                        "[${dto("live")},${dto("old", expires = "2026-11-14T11:00:00+00:00")}]"
                    )
                    auth !in validTokens -> MockResponse().setResponseCode(401)
                    path == "/v1/reports" -> MockResponse().setResponseCode(201).setBody("""{"report":${dto("new")},"merged":false}""")
                    path.endsWith("/votes") -> voteResponse ?: MockResponse().setBody(dto("live"))
                    else -> MockResponse().setResponseCode(404) // live updates hub: not served here
                }
            }
        }
        server.start()
    }

    @After
    fun stop() {
        scope.cancel()
        server.shutdown()
    }

    private fun repo(tokens: DeviceTokenStore = MemoryTokens()) =
        BackendReportRepository(server.url("").toString().trimEnd('/'), tokens, scope, now = { clock })

    @Test
    fun firstReportRegistersTheDeviceOnceAndSendsAnIdempotencyKey() = runBlocking {
        val tokens = MemoryTokens()
        val r = repo(tokens)
        r.submit(ReportType.POLICE, GeoPoint(3.1, 101.6))
        r.submit(ReportType.POLICE, GeoPoint(3.1, 101.6))
        assertEquals(1, registrations)
        assertEquals("token-1-abcdefghijklmnopqrstuvwxyz", tokens.token)
        val posts = requests.filter { it.path == "/v1/reports" }
        assertEquals(2, posts.size)
        assertTrue(posts.all { (it.getHeader("Idempotency-Key")?.length ?: 0) >= 32 })
        assertEquals(listOf("new"), r.reports.value.map { it.id })
    }

    @Test
    fun aForgottenTokenIsReplacedAutomatically() = runBlocking {
        val tokens = MemoryTokens("token-from-a-database-that-was-reset-000")
        repo(tokens).submit(ReportType.CRASH, GeoPoint(3.1, 101.6))
        assertEquals(1, registrations)
        assertEquals("token-1-abcdefghijklmnopqrstuvwxyz", tokens.token)
    }

    @Test
    fun serverRefusalsBecomeReadableMessages() = runBlocking {
        voteResponse = MockResponse().setResponseCode(409).setBody("\"You cannot vote on your own report.\"")
        try {
            repo().confirm("live")
            fail("expected a refusal")
        } catch (e: ReportRejectedException) {
            assertEquals("You cannot vote on your own report.", e.message)
        }
    }

    @Test
    fun loadsTheVisibleAreaSkipsExpiredAndPrunesLater() = runBlocking {
        val r = repo()
        r.watchArea(MapArea(101.55, 3.05, 101.65, 3.15))
        withTimeout(5_000) { while (r.reports.value.isEmpty()) delay(20) }
        assertEquals(listOf("live"), r.reports.value.map { it.id }) // "old" already expired
        val bbox = requests.first { it.path!!.startsWith("/v1/reports?bbox=") }.path!!
        assertTrue(bbox, bbox.contains("101.500000,3.000000,101.700000,3.200000"))

        clock += 3 * 60 * 60_000L // three hours later
        r.pruneExpired()
        assertTrue(r.reports.value.isEmpty())
        r.close()
    }
}
