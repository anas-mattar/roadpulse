package com.roadpulse.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.fixedRateTimer

/**
 * Minimal SignalR client (JSON protocol over WebSockets) for the backend's /hubs/reports.
 * Only what RoadPulse needs: connect, SubscribeArea, receive ReportUpserted/ReportRemoved, keep-alive.
 * Reconnecting is left to the caller ([Listener.onClosed]).
 */
class LiveReportsClient(
    private val baseUrl: String,
    private val listener: Listener,
    private val client: OkHttpClient = Http.client.newBuilder().callTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build(),
) {
    interface Listener {
        /** Handshake done: subscribe and (re)load the list now. */
        fun onConnected()
        fun onUpserted(report: ReportDto)
        fun onRemoved(id: String)
        /** Connection lost or failed. [error] is null for a normal close. */
        fun onClosed(error: Throwable?)
    }

    private var socket: WebSocket? = null
    private var pinger: Timer? = null
    private val invocationId = AtomicInteger()
    @Volatile private var handshakeDone = false
    @Volatile private var closedReported = false

    /** Negotiates and opens the WebSocket. Blocking (call from a background thread). */
    fun connect() {
        val negotiateBody = Http.client.newCall(
            Request.Builder().url("$baseUrl$HUB_PATH/negotiate?negotiateVersion=1").post(ByteArray(0).toRequestBody()).build()
        ).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("Negotiate failed: HTTP ${r.code}")
            r.body!!.string()
        }
        val token = Http.json.decodeFromString(Negotiate.serializer(), negotiateBody).connectionToken
        val wsUrl = baseUrl.replaceFirst("http", "ws") + "$HUB_PATH?id=$token"
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"protocol":"json","version":1}$RS""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                for (frame in SignalR.frames(text)) handle(frame)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = reportClosed(null)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = reportClosed(t)
        })
    }

    /** Watches [area]; replaces any earlier area on the server. */
    fun subscribe(area: MapArea) {
        val id = invocationId.incrementAndGet().toString()
        val msg = buildJsonObject {
            put("type", 1)
            put("invocationId", id)
            put("target", "SubscribeArea")
            put("arguments", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(area.minLon))
                add(kotlinx.serialization.json.JsonPrimitive(area.minLat))
                add(kotlinx.serialization.json.JsonPrimitive(area.maxLon))
                add(kotlinx.serialization.json.JsonPrimitive(area.maxLat))
            })
        }
        socket?.send(msg.toString() + RS)
    }

    fun close() {
        closedReported = true // the caller asked for it: no reconnect callback
        pinger?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    private fun handle(frame: String) {
        if (!handshakeDone) {
            val error = runCatching { Http.json.parseToJsonElement(frame).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            if (error != null) {
                socket?.cancel()
                reportClosed(java.io.IOException("Handshake failed: $error"))
                return
            }
            handshakeDone = true
            // The server drops clients that are silent for 30 s.
            pinger = fixedRateTimer("signalr-ping", daemon = true, initialDelay = PING_MS, period = PING_MS) {
                socket?.send("""{"type":6}$RS""")
            }
            listener.onConnected()
            return
        }
        when (val m = SignalR.parse(frame)) {
            is SignalR.Message.Invocation -> when (m.target) {
                "ReportUpserted" -> m.arguments.firstOrNull()?.let {
                    listener.onUpserted(Http.json.decodeFromJsonElement(ReportDto.serializer(), it))
                }
                "ReportRemoved" -> m.arguments.firstOrNull()?.jsonPrimitive?.contentOrNull?.let(listener::onRemoved)
            }
            is SignalR.Message.Close -> {
                socket?.close(1000, null)
                reportClosed(m.error?.let { java.io.IOException("Server closed: $it") })
            }
            else -> Unit // pings, completions
        }
    }

    private fun reportClosed(error: Throwable?) {
        pinger?.cancel()
        if (closedReported) return
        closedReported = true
        listener.onClosed(error)
    }

    @Serializable
    private data class Negotiate(val connectionToken: String)

    companion object {
        const val HUB_PATH = "/hubs/reports"
        const val RS = "\u001e"
        private const val PING_MS = 15_000L
    }
}

/** SignalR JSON hub protocol: record separator framing and message types. */
object SignalR {
    sealed interface Message {
        data class Invocation(val target: String, val arguments: List<JsonElement>) : Message
        data class Completion(val invocationId: String?, val error: String?) : Message
        data object Ping : Message
        data class Close(val error: String?) : Message
        data class Other(val type: Int) : Message
    }

    /** Splits a WebSocket text message into SignalR messages (each ends with 0x1E). */
    fun frames(text: String): List<String> = text.split(LiveReportsClient.RS).filter { it.isNotBlank() }

    fun parse(frame: String): Message {
        val o: JsonObject = Http.json.parseToJsonElement(frame).jsonObject
        return when (val type = o["type"]?.jsonPrimitive?.intOrNull ?: -1) {
            1 -> Message.Invocation(
                o["target"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                (o["arguments"] as? JsonArray)?.toList().orEmpty(),
            )
            3 -> Message.Completion(o["invocationId"]?.jsonPrimitive?.contentOrNull, o["error"]?.jsonPrimitive?.contentOrNull)
            6 -> Message.Ping
            7 -> Message.Close(o["error"]?.jsonPrimitive?.contentOrNull)
            else -> Message.Other(type)
        }
    }
}
