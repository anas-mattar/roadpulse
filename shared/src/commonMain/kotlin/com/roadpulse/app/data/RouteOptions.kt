package com.roadpulse.app.data

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
    fun summary(s: com.roadpulse.app.i18n.AppStrings = com.roadpulse.app.i18n.EnglishStrings): String? {
        val parts = buildList {
            if (vehicle != Vehicle.CAR) add(s.vehicle(vehicle))
            val avoid = buildList {
                if (avoidTolls) add(s.avoidTollsShort)
                if (avoidHighways) add(s.avoidHighwaysShort)
                if (avoidFerries) add(s.avoidFerriesShort)
            }
            if (avoid.isNotEmpty()) add(s.avoiding(avoid))
            if (allowUnpaved) add(s.unpavedAllowed)
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
