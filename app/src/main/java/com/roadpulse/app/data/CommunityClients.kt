package com.roadpulse.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** A request the server refused, with its HTTP status. */
class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Calls the RoadPulse backend as this anonymous device (ADR 0006): registers on first use and
 * once more if the server no longer knows the token (e.g. the local database was reset).
 */
class DeviceApi(private val baseUrl: String, private val tokens: DeviceTokenStore) {
    private val registerMutex = Mutex()

    suspend fun call(method: String, path: String, body: String? = null): String {
        val token = tokens.load() ?: register()
        return try {
            raw(method, path, body, token)
        } catch (e: ApiException) {
            if (e.code != 401) throw e
            tokens.save(null)
            raw(method, path, body, register())
        }
    }

    private suspend fun register(): String = registerMutex.withLock {
        tokens.load()?.let { return it }
        val text = raw("POST", "/v1/devices", "", null)
        val token = Http.json.decodeFromString(Reg.serializer(), text).token
        tokens.save(token)
        token
    }

    private suspend fun raw(method: String, path: String, body: String?, token: String?): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path)
            .method(method, body?.toRequestBody(JSON))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        Http.client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}: ${text.take(200)}")
            text
        }
    }

    @Serializable private data class Reg(val deviceId: String, val token: String)

    companion object {
        val JSON = "application/json".toMediaType()
    }
}

// ---------------------------------------------------------------- Profile

@Serializable
data class ProfileStats(val reports: Int = 0, val confirmationsReceived: Int = 0, val votes: Int = 0, val removed: Int = 0)

/** Points, level and badges for this device (backend: Modules/Profile). */
@Serializable
data class Profile(
    val nickname: String? = null,
    val points: Int = 0,
    /** LEARNER, ROAD_SCOUT, NAVIGATOR, ROAD_GUARDIAN or ROAD_LEGEND. */
    val level: String = "LEARNER",
    val levelIndex: Int = 0,
    val nextLevelPoints: Int? = null,
    val stats: ProfileStats = ProfileStats(),
    /** FIRST_REPORT, REPORTER_10, REPORTER_50, HELPFUL_10, CHECKER_25. */
    val badges: List<String> = emptyList(),
) {
    /** Progress towards the next level, 0..1 (1 at the top level). */
    fun progress(levelStarts: List<Int> = LEVEL_STARTS): Float {
        val next = nextLevelPoints ?: return 1f
        val from = levelStarts.getOrElse(levelIndex) { 0 }
        return ((points - from).toFloat() / (next - from)).coerceIn(0f, 1f)
    }

    companion object {
        val LEVEL_STARTS = listOf(0, 100, 500, 2_000, 10_000)
        val ALL_BADGES = listOf("FIRST_REPORT", "REPORTER_10", "REPORTER_50", "HELPFUL_10", "CHECKER_25")
    }
}

class ProfileClient(private val api: DeviceApi) {
    suspend fun get(): Profile = Http.json.decodeFromString(Profile.serializer(), api.call("GET", "/v1/me"))

    suspend fun setNickname(name: String?): Profile =
        Http.json.decodeFromString(Profile.serializer(), api.call("PUT", "/v1/me", Http.json.encodeToString(NicknameBody.serializer(), NicknameBody(name))))

    @Serializable private data class NicknameBody(val nickname: String?)
}

// ---------------------------------------------------------------- Share my drive

@Serializable
data class ShareLink(val id: String, val url: String)

class ShareClient(private val api: DeviceApi) {
    suspend fun start(destination: Place, position: GeoPoint, etaMillis: Long, name: String?): ShareLink {
        val body = Start(destination.name.take(120), destination.location.lat, destination.location.lon, position.lat, position.lon, etaMillis, name)
        return Http.json.decodeFromString(ShareLink.serializer(), api.call("POST", "/v1/shares", Http.json.encodeToString(Start.serializer(), body)))
    }

    suspend fun update(id: String, position: GeoPoint, etaMillis: Long, state: String) {
        api.call("PUT", "/v1/shares/$id", Http.json.encodeToString(Update.serializer(), Update(position.lat, position.lon, etaMillis, state)))
    }

    suspend fun stop(id: String) {
        runCatching { api.call("DELETE", "/v1/shares/$id") }
    }

    @Serializable private data class Start(
        val destination: String, val destLat: Double, val destLon: Double, val lat: Double, val lon: Double,
        val etaUnixMs: Long, val name: String?,
    )
    @Serializable private data class Update(val lat: Double, val lon: Double, val etaUnixMs: Long, val state: String)
}

// ---------------------------------------------------------------- Live traffic

/** One GPS sample in an anonymous traffic segment: seconds since the segment began. */
@Serializable
data class TrafficPoint(val lat: Double, val lon: Double, val t: Int, val speedMps: Double?)

/** A coloured road piece on the traffic layer: FREE, SLOW or JAM. */
@Serializable
data class TrafficLine(val edgeId: Long, val level: String, val speedKph: Int, val expectedKph: Int, val points: List<List<Double>>)

/** Traffic uploads (no device token: they are anonymous by design, ADR 0004) and the traffic layer. */
class TrafficClient(private val baseUrl: String) {
    suspend fun upload(points: List<TrafficPoint>) {
        val body = Http.json.encodeToString(Segment.serializer(), Segment(points))
        Http.execute(Request.Builder().url("$baseUrl/v1/traffic/samples").post(body.toRequestBody(DeviceApi.JSON)).build())
    }

    suspend fun lines(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<TrafficLine> {
        val url = "$baseUrl/v1/traffic?minLat=$minLat&minLon=$minLon&maxLat=$maxLat&maxLon=$maxLon"
        return Http.json.decodeFromString(ListSerializer(TrafficLine.serializer()), Http.execute(Request.Builder().url(url).build()))
    }

    @Serializable private data class Segment(val points: List<TrafficPoint>)
}
