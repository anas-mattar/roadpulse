package com.roadpulse.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID

/** Report as sent by the backend (REST and live updates). */
@Serializable
data class ReportDto(
    val id: String,
    val type: ReportType,
    val lat: Double,
    val lon: Double,
    val headingDeg: Int? = null,
    val createdAt: String,
    val expiresAt: String,
    val confirmations: Int = 0,
    val dismissals: Int = 0,
    val subtype: String? = null,
) {
    fun toReport() = Report(
        id = id,
        type = type,
        location = GeoPoint(lat, lon),
        createdAtMillis = OffsetDateTime.parse(createdAt).toInstant().toEpochMilli(),
        confirmations = confirmations,
        dismissals = dismissals,
        subtype = subtype,
        expiresAtMillis = OffsetDateTime.parse(expiresAt).toInstant().toEpochMilli(),
    )
}

@Serializable
private data class CreateReportBody(
    val type: ReportType,
    val lat: Double,
    val lon: Double,
    val subtype: String? = null,
    val durationMinutes: Int? = null,
)

@Serializable
private data class CreateReportResult(val report: ReportDto, val merged: Boolean)

@Serializable
private data class Registration(val deviceId: String, val token: String)

@Serializable
private data class VoteBody(val stillThere: Boolean)

@Serializable
private data class FlagBody(val reason: FlagReason)

/** Thrown when the server refuses an action with a message worth showing (for example 409). */
class ReportRejectedException(message: String) : Exception(message)

/**
 * Reports from the RoadPulse backend: loads the visible area with GET /v1/reports, keeps it current
 * with live updates (/hubs/reports), and sends new reports and confirmations. Registers an
 * anonymous device on first write (ADR 0006). Reports past their expiry are dropped locally.
 */
class BackendReportRepository(
    private val baseUrl: String,
    private val tokens: DeviceTokenStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    /** Diagnostics (connection state only: never locations or tokens). */
    private val log: (String) -> Unit = {},
) : ReportRepository {
    private val state = MutableStateFlow<List<Report>>(emptyList())
    override val reports: StateFlow<List<Report>> = state.asStateFlow()

    private val _live = MutableStateFlow(false)
    /** True while live updates are connected. */
    val live: StateFlow<Boolean> = _live.asStateFlow()

    private val lock = Any()
    private var watched: MapArea? = null
    private var client: LiveReportsClient? = null
    private var connectJob: Job? = null
    private var closed = false
    private val registerMutex = Mutex()
    /** Reports created from this phone in this session (the server also refuses self-votes). */
    private val ownIds = java.util.Collections.synchronizedSet(HashSet<String>())

    init {
        // Drop expired reports (the server does not push natural expiry).
        scope.launch {
            while (isActive) {
                delay(PRUNE_MS)
                pruneExpired()
            }
        }
    }

    override fun watchArea(visible: MapArea) {
        val area: MapArea
        synchronized(lock) {
            if (closed || !MapArea.needsNewSubscription(watched, visible)) return
            area = MapArea.subscriptionFor(visible)
            watched = area
        }
        val c = client
        if (c != null && _live.value) {
            log("Watching a new map area")
            c.subscribe(area)
            scope.launch { reload(area) }
        } else {
            scope.launch { reload(area) } // show something even before live updates connect
            ensureConnecting()
        }
    }

    override suspend fun submit(type: ReportType, location: GeoPoint, subtype: String?, durationMinutes: Int?): Report {
        val body = Http.json.encodeToString(
            CreateReportBody.serializer(), CreateReportBody(type, location.lat, location.lon, subtype, durationMinutes)
        )
        // Same key on retry: the server creates the report only once.
        val key = UUID.randomUUID().toString()
        val text = withAuth { token ->
            post("/v1/reports", body, token, idempotencyKey = key, retries = 1)
        }
        val result = Http.json.decodeFromString(CreateReportResult.serializer(), text)
        val report = result.report.toReport()
        if (!result.merged) ownIds += report.id // merged = you confirmed someone else's report
        upsert(report)
        return report
    }

    override fun isOwn(reportId: String) = reportId in ownIds

    override suspend fun vote(reportId: String, stillThere: Boolean) {
        val body = Http.json.encodeToString(VoteBody.serializer(), VoteBody(stillThere))
        val text = withAuth { token -> post("/v1/reports/$reportId/votes", body, token) }
        // A "not there" vote may end the report; upsert drops it when it has expired.
        upsert(Http.json.decodeFromString(ReportDto.serializer(), text).toReport())
    }

    override suspend fun flag(reportId: String, reason: FlagReason) {
        val body = Http.json.encodeToString(FlagBody.serializer(), FlagBody(reason))
        withAuth { token -> post("/v1/reports/$reportId/flags", body, token) }
    }

    override fun close() {
        synchronized(lock) { closed = true }
        connectJob?.cancel()
        client?.close()
        client = null
        _live.value = false
    }

    // --- live updates -------------------------------------------------------------------------

    private fun ensureConnecting() {
        synchronized(lock) {
            if (closed || connectJob?.isActive == true || client != null) return
            connectJob = scope.launch { connectWithBackoff() }
        }
    }

    private suspend fun connectWithBackoff() {
        var wait = 2_000L
        while (scope.isActive && !closed) {
            val c = LiveReportsClient(baseUrl, object : LiveReportsClient.Listener {
                override fun onConnected() {
                    log("Live updates connected")
                    _live.value = true
                    watched?.let { area ->
                        client?.subscribe(area)
                        scope.launch { reload(area) } // catch up on anything missed while offline
                    }
                }
                override fun onUpserted(report: ReportDto) {
                    log("Live: report ${report.type} updated")
                    upsert(report.toReport())
                }
                override fun onRemoved(id: String) {
                    log("Live: report removed")
                    state.update { list -> list.filterNot { it.id == id } }
                }
                override fun onClosed(error: Throwable?) {
                    log("Live updates closed: ${error?.let { it::class.simpleName + " " + it.message } ?: "normal"}")
                    _live.value = false
                    client = null
                    if (!closed) ensureConnecting()
                }
            })
            try {
                client = c
                log("Connecting live updates to $baseUrl")
                withContext(Dispatchers.IO) { c.connect() }
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                client = null
                log("Live connect failed (${e::class.simpleName}: ${e.message}); retry in ${wait / 1000} s")
                delay(wait)
                wait = (wait * 2).coerceAtMost(30_000L)
            }
        }
    }

    // --- state ---------------------------------------------------------------------------------

    private suspend fun reload(area: MapArea) {
        val list = runCatching {
            val text = Http.execute(Request.Builder().url("$baseUrl/v1/reports?bbox=${area.bboxParam()}").build())
            Http.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ReportDto.serializer()), text)
        }.onFailure { log("Loading reports failed: ${it::class.simpleName} ${it.message}") }
            .getOrNull() ?: return // offline: keep what we have
        log("Loaded ${list.size} reports for the map area")
        if (watched != area) return // the map moved on meanwhile
        val t = now()
        state.value = list.map { it.toReport() }.filter { (it.expiresAtMillis ?: Long.MAX_VALUE) > t }
    }

    private fun upsert(report: Report) {
        if ((report.expiresAtMillis ?: Long.MAX_VALUE) <= now()) {
            state.update { list -> list.filterNot { it.id == report.id } }
            return
        }
        state.update { list -> list.filterNot { it.id == report.id } + report }
    }

    internal fun pruneExpired() {
        val t = now()
        state.update { list -> list.filter { (it.expiresAtMillis ?: Long.MAX_VALUE) > t } }
    }

    // --- HTTP ----------------------------------------------------------------------------------

    /** Runs [call] with the device token; registers first if needed, and once more if the server forgot us. */
    private suspend fun withAuth(call: suspend (String) -> String): String {
        val token = tokens.load() ?: register()
        return try {
            call(token)
        } catch (e: UnauthorizedException) {
            tokens.save(null) // e.g. the local database was reset
            call(register())
        }
    }

    private suspend fun register(): String = registerMutex.withLock {
        tokens.load()?.let { return it }
        val text = postRaw("/v1/devices", "", null, null)
        val token = Http.json.decodeFromString(Registration.serializer(), text).token
        tokens.save(token)
        token
    }

    private suspend fun post(path: String, body: String, token: String, idempotencyKey: String? = null, retries: Int = 0): String {
        var attempt = 0
        while (true) {
            try {
                return postRaw(path, body, token, idempotencyKey)
            } catch (e: IOException) {
                if (e is HttpStatusException || attempt >= retries) throw e
                attempt++
                delay(1_000)
            }
        }
    }

    private suspend fun postRaw(path: String, body: String, token: String?, idempotencyKey: String?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody(JSON))
            .apply {
                token?.let { header("Authorization", "Bearer $it") }
                idempotencyKey?.let { header("Idempotency-Key", it) }
            }
            .build()
        Http.client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.isSuccessful -> text
                r.code == 401 -> throw UnauthorizedException()
                r.code == 404 -> throw ReportRejectedException("That report is no longer active.")
                r.code == 409 -> throw ReportRejectedException(text.trim('"').ifBlank { "Not allowed." })
                r.code == 429 -> throw ReportRejectedException("Too many requests. Please wait a few minutes and try again.")
                else -> throw HttpStatusException(r.code, text.take(200))
            }
        }
    }

    private open class HttpStatusException(code: Int, body: String) : IOException("HTTP $code: $body")
    private class UnauthorizedException : HttpStatusException(401, "unauthorized")

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val PRUNE_MS = 15_000L
    }
}
