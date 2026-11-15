package com.roadpulse.app.nav

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Map appearance and driving-display preferences, kept across restarts. */
object DisplaySettings {
    private var prefs: SharedPreferences? = null
    private val _mapColors = MutableStateFlow(MapColors.AUTO)
    val mapColors: StateFlow<MapColors> = _mapColors.asStateFlow()

    /** Keep north at the top (no map rotation), also while driving. */
    val northUp = MutableStateFlow(false)
    /** Show a compass button on the map (tap it to turn north up). */
    val showCompass = MutableStateFlow(false)
    /** Flat 2D map while driving instead of the tilted view. */
    val flatDriving = MutableStateFlow(false)
    val showSpeedometer = MutableStateFlow(true)
    val limitDisplay = MutableStateFlow(LimitDisplay.ALWAYS)
    /** km/h over the limit before the speed turns red (and the alert sounds). */
    val speedingMarginKmh = MutableStateFlow(3)
    /** Short sound when the speed goes over limit + margin. */
    val speedingSound = MutableStateFlow(false)

    /** Saves one of the flags above (keyed by name) and updates it. */
    fun <T> set(flow: MutableStateFlow<T>, key: String, value: T) {
        flow.value = value
        prefs?.edit()?.apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is Enum<*> -> putString(key, value.name)
            }
        }?.apply()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _mapColors.value = runCatching { MapColors.valueOf(p.getString(KEY, MapColors.AUTO.name)!!) }
            .getOrDefault(MapColors.AUTO)
        northUp.value = p.getBoolean("north_up", false)
        showCompass.value = p.getBoolean("show_compass", false)
        flatDriving.value = p.getBoolean("flat_driving", false)
        showSpeedometer.value = p.getBoolean("show_speedometer", true)
        limitDisplay.value = runCatching { LimitDisplay.valueOf(p.getString("limit_display", LimitDisplay.ALWAYS.name)!!) }
            .getOrDefault(LimitDisplay.ALWAYS)
        speedingMarginKmh.value = p.getInt("speeding_margin", 3)
        speedingSound.value = p.getBoolean("speeding_sound", false)
    }

    fun setMapColors(value: MapColors) {
        _mapColors.value = value
        prefs?.edit()?.putString(KEY, value.name)?.apply()
    }

    private const val KEY = "map_colors"
}
