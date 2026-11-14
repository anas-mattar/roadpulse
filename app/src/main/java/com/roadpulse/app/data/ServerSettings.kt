package com.roadpulse.app.data

import android.content.Context
import android.content.SharedPreferences
import com.roadpulse.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which servers the app talks to. Debug builds can switch to the developer's own Docker stack
 * (backend/docker-compose.yml, reached from the emulator at 10.0.2.2). Release builds always use
 * the configured HTTPS servers; with no backend configured, reports stay on the phone.
 */
object ServerSettings {
    private var prefs: SharedPreferences? = null

    /** True if this build has a local server configured (debug builds only). */
    val localAvailable: Boolean get() = BuildConfig.DEBUG && BuildConfig.LOCAL_BACKEND_URL.isNotEmpty()

    private val _useLocal = MutableStateFlow(localAvailable)
    val useLocal: StateFlow<Boolean> = _useLocal.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _useLocal.value = localAvailable && p.getBoolean(KEY, true)
    }

    fun setUseLocal(value: Boolean) {
        if (!localAvailable) return
        _useLocal.value = value
        prefs?.edit()?.putBoolean(KEY, value)?.apply()
    }

    fun routingUrl(): String = if (_useLocal.value) BuildConfig.LOCAL_ROUTING_URL else BuildConfig.ROUTING_URL

    /** Empty when there is no reports server (reports then stay on this phone). */
    fun backendUrl(): String = if (_useLocal.value) BuildConfig.LOCAL_BACKEND_URL else BuildConfig.BACKEND_URL

    /** Where the anonymous device token for [baseUrl] is kept (one token per server). */
    fun tokenStore(context: Context, baseUrl: String): DeviceTokenStore {
        val p = context.applicationContext.getSharedPreferences("roadpulse_device", Context.MODE_PRIVATE)
        val key = "token@$baseUrl"
        return object : DeviceTokenStore {
            override fun load(): String? = p.getString(key, null)
            override fun save(token: String?) {
                p.edit().apply { if (token == null) remove(key) else putString(key, token) }.apply()
            }
        }
    }

    private const val KEY = "use_local_server"
}

/** Keeps the anonymous device token (ADR 0006). It identifies the install, not the person. */
interface DeviceTokenStore {
    fun load(): String?
    fun save(token: String?)
}
