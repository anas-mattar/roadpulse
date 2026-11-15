package com.roadpulse.app.ios

import com.roadpulse.app.data.RouteOptions
import com.roadpulse.app.data.Vehicle
import com.roadpulse.app.i18n.stringsFor
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.LimitDisplay
import com.roadpulse.app.nav.MapColors
import com.roadpulse.app.nav.SunTimes

/**
 * Settings choices for the iPhone screens as (name, label in the app language) pairs, so Swift
 * stores plain names and never depends on how Kotlin enums are spelled there.
 */
object Choices {
    fun vehicles(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return Vehicle.entries.map { listOf(it.name, s.vehicle(it)) }
    }

    fun mapColors(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return MapColors.entries.map { listOf(it.name, s.mapColors(it)) }
    }

    fun limitDisplays(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return LimitDisplay.entries.map { listOf(it.name, s.limitDisplay(it)) }
    }

    fun routeOptions(vehicleName: String, avoidTolls: Boolean, avoidHighways: Boolean, avoidFerries: Boolean, allowUnpaved: Boolean) =
        RouteOptions(
            vehicle = Vehicle.entries.firstOrNull { it.name == vehicleName } ?: Vehicle.CAR,
            avoidTolls = avoidTolls, avoidHighways = avoidHighways, avoidFerries = avoidFerries, allowUnpaved = allowUnpaved,
        )

    /** "Motorcycle · avoiding tolls" for the route preview; null with the usual options. */
    fun summary(options: RouteOptions, language: GuidanceLanguage): String? = options.summary(stringsFor(language))

    /** Dark map now? [mapColorsName] is AUTO (dark after sunset), LIGHT or DARK. */
    fun darkMap(mapColorsName: String, lat: Double, lon: Double, nowMillis: Long): Boolean = when (mapColorsName) {
        MapColors.DARK.name -> true
        MapColors.LIGHT.name -> false
        else -> SunTimes.isNight(lat, lon, nowMillis)
    }

    /** Whether the speed limit sign shows, for a [LimitDisplay] name. */
    fun showLimit(limitDisplayName: String, speeding: Boolean): Boolean = when (limitDisplayName) {
        LimitDisplay.NEVER.name -> false
        LimitDisplay.WHEN_SPEEDING.name -> speeding
        else -> true
    }
}
