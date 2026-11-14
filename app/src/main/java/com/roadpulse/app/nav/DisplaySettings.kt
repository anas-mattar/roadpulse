package com.roadpulse.app.nav

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MapColors(val label: String) {
    AUTO("Automatic (dark after sunset)"),
    LIGHT("Always light"),
    DARK("Always dark"),
}

/** Map appearance preferences, kept across restarts. */
object DisplaySettings {
    private var prefs: SharedPreferences? = null
    private val _mapColors = MutableStateFlow(MapColors.AUTO)
    val mapColors: StateFlow<MapColors> = _mapColors.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _mapColors.value = runCatching { MapColors.valueOf(p.getString(KEY, MapColors.AUTO.name)!!) }
            .getOrDefault(MapColors.AUTO)
    }

    fun setMapColors(value: MapColors) {
        _mapColors.value = value
        prefs?.edit()?.putString(KEY, value.name)?.apply()
    }

    private const val KEY = "map_colors"
}
