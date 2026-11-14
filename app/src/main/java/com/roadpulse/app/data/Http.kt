package com.roadpulse.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        // Upper bound for a whole request, so a weak network cannot stall a reroute for long.
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        // Request bodies rely on default values (e.g. costing = "auto"); without this they are omitted.
        encodeDefaults = true
    }

    suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${body.take(200)}")
            body
        }
    }
}
