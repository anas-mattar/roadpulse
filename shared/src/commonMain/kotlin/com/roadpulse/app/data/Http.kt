package com.roadpulse.app.data

import kotlinx.serialization.json.Json

/** A network failure (java.io.IOException on Android). */
expect open class IOException(message: String) : Exception

/** A request the server refused, with its HTTP status. */
class ApiException(val code: Int, message: String) : IOException(message)

class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Sends one HTTP request; each platform has its own (OkHttp on Android, URLSession on iPhone). */
interface HttpTransport {
    /** Throws [IOException] when the server cannot be reached. A [body] is sent as JSON. */
    suspend fun send(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): HttpResult
}

expect fun platformTransport(): HttpTransport

/** Shared HTTP access for the routing, search and RoadPulse servers. */
object Http {
    var transport: HttpTransport = platformTransport()

    val json = Json {
        ignoreUnknownKeys = true
        // Request bodies rely on default values (e.g. costing = "auto"); without this they are omitted.
        encodeDefaults = true
        // Leave out fields that are null (e.g. "alternates" on reroute requests).
        explicitNulls = false
    }

    /** The response text; throws [IOException] for network errors and non-2xx answers. */
    suspend fun execute(url: String, method: String = "GET", body: String? = null, headers: Map<String, String> = emptyMap()): String {
        val r = transport.send(method, url, body, headers)
        if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${r.body.take(200)}")
        return r.body
    }

    /** [base] plus query parameters, percent-encoded. */
    fun url(base: String, vararg params: Pair<String, String>): String =
        if (params.isEmpty()) base else base + "?" + params.joinToString("&") { (k, v) -> encode(k) + "=" + encode(v) }

    /** Percent-encodes a query value (UTF-8; letters, digits and -._~ stay). */
    fun encode(value: String): String {
        val sb = StringBuilder()
        for (b in value.encodeToByteArray()) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') sb.append(ch)
            else sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789ABCDEF"
    val USER_AGENT = mapOf("User-Agent" to "RoadPulse/0.1")
}

/** Keeps the anonymous device token (ADR 0006). It identifies the install, not the person. */
interface DeviceTokenStore {
    fun load(): String?
    fun save(token: String?)
}
