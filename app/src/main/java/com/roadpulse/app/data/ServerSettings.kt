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

    /** Map tiles from the developer's own tile server (off by default: it needs a one-time setup). */
    val localMapsAvailable: Boolean get() = BuildConfig.DEBUG && BuildConfig.LOCAL_MAP_STYLE_URL.isNotEmpty()
    private val _useLocalMaps = MutableStateFlow(false)
    val useLocalMaps: StateFlow<Boolean> = _useLocalMaps.asStateFlow()

    /**
     * How a debug build reaches the laptop: the emulator sees it at 10.0.2.2; a real phone reaches
     * it over the USB cable (or wireless debugging) after connect-phone.bat runs "adb reverse",
     * which makes the laptop's ports appear on the phone's own 127.0.0.1. Nothing is opened to the
     * Wi-Fi network.
     */
    enum class LaptopLink(val label: String, val host: String) {
        EMULATOR("Emulator", "10.0.2.2"),
        USB("Phone on USB cable (connect-phone.bat)", "127.0.0.1"),
    }

    private val _link = MutableStateFlow(LaptopLink.EMULATOR)
    val link: StateFlow<LaptopLink> = _link.asStateFlow()

    fun setLink(value: LaptopLink) {
        _link.value = value
        prefs?.edit()?.putString(KEY_LINK, value.name)?.apply()
    }

    /** A configured local URL (written for the emulator) pointed at the chosen [link]. Pure: tested. */
    fun forLink(url: String, link: LaptopLink): String =
        if (url.isEmpty()) url else url.replaceFirst("//10.0.2.2", "//" + link.host)

    /** True when running in the Android emulator (default link on first start). */
    private fun isEmulator(): Boolean =
        android.os.Build.HARDWARE.let { it == "ranchu" || it == "goldfish" } ||
            android.os.Build.FINGERPRINT.startsWith("generic") || android.os.Build.PRODUCT.contains("sdk")

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _link.value = p.getString(KEY_LINK, null)?.let { runCatching { LaptopLink.valueOf(it) }.getOrNull() }
            ?: if (isEmulator()) LaptopLink.EMULATOR else LaptopLink.USB
        _useLocal.value = localAvailable && p.getBoolean(KEY, true)
        _useLocalMaps.value = localMapsAvailable && p.getBoolean(KEY_MAPS, false)
    }

    fun setUseLocalMaps(value: Boolean) {
        if (!localMapsAvailable) return
        _useLocalMaps.value = value
        prefs?.edit()?.putBoolean(KEY_MAPS, value)?.apply()
    }

    /** Map style for day or night: the developer's tile server when chosen, otherwise the public one. */
    fun mapStyleUrl(dark: Boolean): String = when {
        _useLocalMaps.value && dark -> forLink(BuildConfig.LOCAL_MAP_STYLE_DARK_URL, _link.value)
        _useLocalMaps.value -> forLink(BuildConfig.LOCAL_MAP_STYLE_URL, _link.value)
        dark -> BuildConfig.MAP_STYLE_DARK_URL
        else -> BuildConfig.MAP_STYLE_URL
    }

    fun setUseLocal(value: Boolean) {
        if (!localAvailable) return
        _useLocal.value = value
        prefs?.edit()?.putBoolean(KEY, value)?.apply()
    }

    fun routingUrl(): String = if (_useLocal.value) forLink(BuildConfig.LOCAL_ROUTING_URL, _link.value) else BuildConfig.ROUTING_URL

    /** Empty when there is no reports server (reports then stay on this phone). */
    fun backendUrl(): String = if (_useLocal.value) forLink(BuildConfig.LOCAL_BACKEND_URL, _link.value) else BuildConfig.BACKEND_URL

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
    private const val KEY_MAPS = "use_local_maps"
    private const val KEY_LINK = "laptop_link"
}

/** Keeps the anonymous device token (ADR 0006). It identifies the install, not the person. */
interface DeviceTokenStore {
    fun load(): String?
    fun save(token: String?)
}
