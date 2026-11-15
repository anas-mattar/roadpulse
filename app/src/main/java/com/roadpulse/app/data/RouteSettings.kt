package com.roadpulse.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The driver's route options, kept across restarts. */
object RouteSettings {
    private var prefs: SharedPreferences? = null
    private val _options = MutableStateFlow(RouteOptions())
    val options: StateFlow<RouteOptions> = _options.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _options.value = RouteOptions(
            vehicle = runCatching { Vehicle.valueOf(p.getString("vehicle", Vehicle.CAR.name)!!) }.getOrDefault(Vehicle.CAR),
            avoidTolls = p.getBoolean("avoid_tolls", false),
            avoidHighways = p.getBoolean("avoid_highways", false),
            avoidFerries = p.getBoolean("avoid_ferries", false),
            allowUnpaved = p.getBoolean("allow_unpaved", false),
        )
    }

    fun update(change: (RouteOptions) -> RouteOptions) {
        val o = change(_options.value)
        _options.value = o
        prefs?.edit()
            ?.putString("vehicle", o.vehicle.name)
            ?.putBoolean("avoid_tolls", o.avoidTolls)
            ?.putBoolean("avoid_highways", o.avoidHighways)
            ?.putBoolean("avoid_ferries", o.avoidFerries)
            ?.putBoolean("allow_unpaved", o.allowUnpaved)
            ?.apply()
    }
}
