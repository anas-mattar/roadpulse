package com.roadpulse.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Vehicle types the routing server plans for (Valhalla costing models). */
enum class Vehicle(val label: String, val costing: String) {
    CAR("Car", "auto"),
    MOTORCYCLE("Motorcycle", "motorcycle"),
    TAXI("Taxi", "taxi"),
}

/** How routes are planned: vehicle and roads to avoid. Avoiding is a strong preference, not a ban. */
data class RouteOptions(
    val vehicle: Vehicle = Vehicle.CAR,
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
    val allowUnpaved: Boolean = false,
) {
    /** Short description for the route preview, e.g. "Motorcycle · avoiding tolls"; null for the defaults. */
    fun summary(): String? {
        val parts = buildList {
            if (vehicle != Vehicle.CAR) add(vehicle.label)
            val avoid = buildList {
                if (avoidTolls) add("tolls")
                if (avoidHighways) add("highways")
                if (avoidFerries) add("ferries")
            }
            if (avoid.isNotEmpty()) add("avoiding " + avoid.joinToString(", "))
            if (allowUnpaved) add("unpaved roads allowed")
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** Valhalla "costing_options" for this vehicle. Pure: unit tested. */
    fun costingOptions(): JsonObject = buildJsonObject {
        put(vehicle.costing, buildJsonObject {
            if (avoidTolls) put("use_tolls", 0.0)
            if (avoidHighways) put("use_highways", 0.0)
            if (avoidFerries) put("use_ferry", 0.0)
            // Unpaved roads are left out unless allowed (car and taxi models support this switch).
            if (!allowUnpaved && vehicle != Vehicle.MOTORCYCLE) put("exclude_unpaved", true)
        })
    }
}

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
