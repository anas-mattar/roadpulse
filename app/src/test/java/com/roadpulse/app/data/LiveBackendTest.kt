package com.roadpulse.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.random.Random

/**
 * End-to-end against a running backend (skipped unless ROADPULSE_BACKEND_URL is set), e.g.
 *   ROADPULSE_BACKEND_URL=http://localhost:8080 ROADPULSE_MODERATOR_KEY=... ./gradlew testDebugUnitTest
 * Two "phones" watch the same area: one reports, the other sees it arrive live, confirms it, and
 * (with a moderator key) sees the moderator's removal arrive live.
 */
class LiveBackendTest {
    private val url = System.getenv("ROADPULSE_BACKEND_URL")
    private val moderatorKey = System.getenv("ROADPULSE_MODERATOR_KEY")

    private class MemoryTokens : DeviceTokenStore {
        var token: String? = null
        override fun load() = token
        override fun save(token: String?) { this.token = token }
    }

    private suspend fun waitFor(what: String, check: () -> Boolean) {
        try {
            withTimeout(10_000) { while (!check()) delay(50) }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for: $what")
        }
    }

    @Test
    fun reportsTravelLiveBetweenTwoPhones() = runBlocking {
        assumeTrue("ROADPULSE_BACKEND_URL not set", !url.isNullOrBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            // A random spot in Johor so repeated runs do not merge with earlier reports.
            val p = GeoPoint(1.5 + Random.nextDouble(0.0, 0.3), 103.5 + Random.nextDouble(0.0, 0.3))
            val visible = MapArea(p.lon - 0.02, p.lat - 0.02, p.lon + 0.02, p.lat + 0.02)
            val alice = BackendReportRepository(url!!, MemoryTokens(), scope)
            val bob = BackendReportRepository(url, MemoryTokens(), scope)
            bob.watchArea(visible)
            waitFor("bob's live connection") { bob.live.value }

            val sent = alice.submit(ReportType.HAZARD, p, subtype = "FLOOD")
            waitFor("bob to receive the report live") { bob.reports.value.any { it.id == sent.id } }
            assertEquals("FLOOD", bob.reports.value.first { it.id == sent.id }.subtype)

            bob.vote(sent.id, stillThere = true)
            alice.watchArea(visible)
            waitFor("alice to see bob's confirmation") {
                alice.reports.value.firstOrNull { it.id == sent.id }?.confirmations == 1
            }

            if (!moderatorKey.isNullOrBlank()) {
                val res = OkHttpClient().newCall(
                    Request.Builder().url("$url/v1/moderation/reports/${sent.id}/remove")
                        .header("X-Moderator-Key", moderatorKey)
                        .post("""{"reason":"End-to-end test"}""".toRequestBody("application/json".toMediaType()))
                        .build()
                ).execute()
                assertEquals(204, res.code)
                waitFor("bob to see the removal live") { bob.reports.value.none { it.id == sent.id } }
            }
            alice.close()
            bob.close()
        } finally {
            scope.cancel()
        }
    }

    /** A phone left on the map for a while must still get pushes (keep-alive, timeouts). */
    @Test
    fun liveConnectionSurvivesAQuietMinute() = runBlocking {
        assumeTrue("ROADPULSE_BACKEND_URL not set", !url.isNullOrBlank())
        assumeTrue("set ROADPULSE_LONG_TEST=1 to run", System.getenv("ROADPULSE_LONG_TEST") == "1")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = GeoPoint(1.5 + Random.nextDouble(0.0, 0.3), 103.5 + Random.nextDouble(0.0, 0.3))
            val bob = BackendReportRepository(url!!, MemoryTokens(), scope)
            bob.watchArea(MapArea(p.lon - 0.02, p.lat - 0.02, p.lon + 0.02, p.lat + 0.02))
            waitFor("bob's live connection") { bob.live.value }
            var drops = 0
            val watcher = scope.launch { bob.live.collect { if (!it) drops++ } }
            delay(60_000)
            val sent = BackendReportRepository(url, MemoryTokens(), scope).submit(ReportType.CRASH, p)
            waitFor("bob to receive the report after a quiet minute") { bob.reports.value.any { it.id == sent.id } }
            watcher.cancel()
            assertEquals("live connection dropped during the quiet minute", 0, drops) // collect starts with true
            bob.close()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun twoNotThereVotesRemoveAReportForEveryone() = runBlocking {
        assumeTrue("ROADPULSE_BACKEND_URL not set", !url.isNullOrBlank())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val p = GeoPoint(1.5 + Random.nextDouble(0.0, 0.3), 103.5 + Random.nextDouble(0.0, 0.3))
            val visible = MapArea(p.lon - 0.02, p.lat - 0.02, p.lon + 0.02, p.lat + 0.02)
            val reporter = BackendReportRepository(url!!, MemoryTokens(), scope)
            val watcher = BackendReportRepository(url, MemoryTokens(), scope)
            watcher.watchArea(visible)
            waitFor("live connection") { watcher.live.value }
            val sent = reporter.submit(ReportType.TRAFFIC_JAM, p)
            assertEquals(true, reporter.isOwn(sent.id))
            waitFor("report to arrive") { watcher.reports.value.any { it.id == sent.id } }

            BackendReportRepository(url, MemoryTokens(), scope).flag(sent.id, FlagReason.WRONG_LOCATION) // accepted (204)
            BackendReportRepository(url, MemoryTokens(), scope).vote(sent.id, stillThere = false)
            BackendReportRepository(url, MemoryTokens(), scope).vote(sent.id, stillThere = false)
            waitFor("the report to disappear live") { watcher.reports.value.none { it.id == sent.id } }

            try {
                reporter.vote(sent.id, stillThere = true)
                throw AssertionError("voting on an ended report should be refused")
            } catch (e: ReportRejectedException) {
                assertEquals("That report is no longer active.", e.message)
            }
            watcher.close()
        } finally {
            scope.cancel()
        }
    }
}
