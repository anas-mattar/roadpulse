package com.roadpulse.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

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

    private suspend fun raw(method: String, path: String, body: String?, token: String?): String {
        val headers = token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        val r = Http.transport.send(method, baseUrl + path, body, headers)
        if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}: ${r.body.take(200)}")
        return r.body
    }

    @Serializable private data class Reg(val deviceId: String, val token: String)
}

// ---------------------------------------------------------------- Profile

class ProfileClient(private val api: DeviceApi) {
    suspend fun get(): Profile = Http.json.decodeFromString(Profile.serializer(), api.call("GET", "/v1/me"))

    suspend fun setNickname(name: String?): Profile =
        Http.json.decodeFromString(Profile.serializer(), api.call("PUT", "/v1/me", Http.json.encodeToString(NicknameBody.serializer(), NicknameBody(name))))

    @Serializable private data class NicknameBody(val nickname: String?)
}

// ---------------------------------------------------------------- Privacy

/** The driver's data rights: a copy of their server data, and deleting it (backend: Modules/Privacy). */
class PrivacyClient(private val api: DeviceApi) {
    /** Everything the server holds about this device, as JSON text. */
    suspend fun export(): String = api.call("GET", "/v1/me/export")

    suspend fun deleteAll() {
        api.call("DELETE", "/v1/me")
    }
}

// ---------------------------------------------------------------- Share my drive

class ShareClient(private val api: DeviceApi) {
    suspend fun start(destination: Place, position: GeoPoint, etaMillis: Long, name: String?): ShareLink {
        val body = Start(destination.name.take(120), destination.location.lat, destination.location.lon, position.lat, position.lon, etaMillis, name)
        return Http.json.decodeFromString(ShareLink.serializer(), api.call("POST", "/v1/shares", Http.json.encodeToString(Start.serializer(), body)))
    }

    /** Sends the position; returns how many people have the share page open (0 if unknown). */
    suspend fun update(id: String, position: GeoPoint, etaMillis: Long, state: String): Int {
        val body = api.call("PUT", "/v1/shares/$id", Http.json.encodeToString(Update.serializer(), Update(position.lat, position.lon, etaMillis, state)))
        return runCatching { Http.json.decodeFromString(Viewers.serializer(), body).viewers }.getOrDefault(0)
    }

    suspend fun stop(id: String) {
        runCatching { api.call("DELETE", "/v1/shares/$id") }
    }

    @Serializable private data class Start(
        val destination: String, val destLat: Double, val destLon: Double, val lat: Double, val lon: Double,
        val etaUnixMs: Long, val name: String?,
    )
    @Serializable private data class Update(val lat: Double, val lon: Double, val etaUnixMs: Long, val state: String)
    @Serializable private data class Viewers(val viewers: Int = 0)
}

// ---------------------------------------------------------------- Live traffic

/** Traffic uploads (no device token: they are anonymous by design, ADR 0004) and the traffic layer. */
class TrafficClient(private val baseUrl: String) {
    suspend fun upload(points: List<TrafficPoint>) {
        val body = Http.json.encodeToString(Segment.serializer(), Segment(points))
        Http.execute("$baseUrl/v1/traffic/samples", "POST", body)
    }

    suspend fun lines(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<TrafficLine> {
        val url = "$baseUrl/v1/traffic?minLat=$minLat&minLon=$minLon&maxLat=$maxLat&maxLon=$maxLon"
        return Http.json.decodeFromString(ListSerializer(TrafficLine.serializer()), Http.execute(url))
    }

    @Serializable private data class Segment(val points: List<TrafficPoint>)
}
