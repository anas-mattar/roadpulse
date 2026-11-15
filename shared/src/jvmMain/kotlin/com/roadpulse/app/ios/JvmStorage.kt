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

/** Tests on the JVM: saved places kept in memory. */
actual fun phonePlacesFile(): com.roadpulse.app.data.TextFile = object : com.roadpulse.app.data.TextFile {
    private var text: String? = null
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
    override fun delete() { text = null }
}
