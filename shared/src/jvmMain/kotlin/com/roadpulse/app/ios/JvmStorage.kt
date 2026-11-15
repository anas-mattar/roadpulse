package com.roadpulse.app.ios

import com.roadpulse.app.data.DeviceTokenStore

/** Tests on the JVM: tokens kept in memory. (The Android app has its own store.) */
private val memory = mutableMapOf<String, String>()

actual fun tokenStoreFor(baseUrl: String): DeviceTokenStore = object : DeviceTokenStore {
    override fun load(): String? = memory[baseUrl]
    override fun save(token: String?) {
        if (token == null) memory.remove(baseUrl) else memory[baseUrl] = token
    }
}
