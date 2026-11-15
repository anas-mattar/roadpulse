package com.roadpulse.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

actual typealias IOException = java.io.IOException

actual fun platformTransport(): HttpTransport = OkHttpTransport

/** Android: OkHttp. [client] is also used for the live-updates WebSocket. */
object OkHttpTransport : HttpTransport {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        // Upper bound for a whole request, so a weak network cannot stall a reroute for long.
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    val JSON = "application/json".toMediaType()

    override suspend fun send(method: String, url: String, body: String?, headers: Map<String, String>): HttpResult =
        withContext(Dispatchers.IO) {
            val needsBody = method == "POST" || method == "PUT" || method == "PATCH"
            val request = Request.Builder().url(url)
                .method(method, body?.toRequestBody(JSON) ?: if (needsBody) "".toRequestBody(JSON) else null)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(request).execute().use { r -> HttpResult(r.code, r.body?.string().orEmpty()) }
        }
}
