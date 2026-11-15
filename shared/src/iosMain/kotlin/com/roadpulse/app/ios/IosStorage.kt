package com.roadpulse.app.ios

import com.roadpulse.app.data.DeviceTokenStore
import platform.Foundation.NSUserDefaults

/** Keeps the anonymous device token for one server in the app's own settings (one token per server). */
class UserDefaultsTokenStore(baseUrl: String) : DeviceTokenStore {
    private val key = "token@$baseUrl"
    override fun load(): String? = NSUserDefaults.standardUserDefaults.stringForKey(key)
    override fun save(token: String?) {
        if (token == null) NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
        else NSUserDefaults.standardUserDefaults.setObject(token, key)
    }
}

actual fun tokenStoreFor(baseUrl: String): DeviceTokenStore = UserDefaultsTokenStore(baseUrl)
