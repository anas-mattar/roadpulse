package com.roadpulse.app.nav

import com.roadpulse.app.data.Route
import kotlin.math.roundToInt

/** What a route card says about one route compared with the others. Pure Kotlin: unit tested. */
data class RouteChoice(
    /** Short tag: "Fastest", "No tolls", "Shortest", or "+5 min". */
    val tag: String,
    /** The main road that makes this route different, e.g. "E37"; null if none stands out. */
    val via: String?,
)

object RouteChoices {

    fun describe(routes: List<Route>, s: com.roadpulse.app.i18n.AppStrings = com.roadpulse.app.i18n.EnglishStrings): List<RouteChoice> {
        if (routes.isEmpty()) return emptyList()
        val fastest = routes.minOf { it.timeSeconds }
        val shortest = routes.minOf { it.lengthKm }
        val anyToll = routes.any { it.hasToll }
        val kmByRoad = routes.map(::kmPerRoad)
        return routes.mapIndexed { i, r ->
            val extraMin = ((r.timeSeconds - fastest) / 60).roundToInt()
            val tag = when {
                r.timeSeconds <= fastest -> s.tagFastest
                anyToll && !r.hasToll -> s.tagNoTolls
                r.lengthKm <= shortest && r.lengthKm < routes[0].lengthKm - 0.2 -> s.tagShortest
                else -> s.tagExtraMinutes(extraMin)
            }
            RouteChoice(tag, distinctiveRoad(i, kmByRoad))
        }
    }

    /** Kilometres driven on each named road. */
    private fun kmPerRoad(route: Route): Map<String, Double> {
        val km = HashMap<String, Double>()
        route.maneuvers.forEach { m -> m.streetNames.firstOrNull()?.let { km[it] = (km[it] ?: 0.0) + m.lengthKm } }
        return km
    }

    /** The road with the most kilometres on this route that the other routes use least. */
    private fun distinctiveRoad(index: Int, kmByRoad: List<Map<String, Double>>): String? {
        val mine = kmByRoad[index]
        val others = kmByRoad.filterIndexed { i, _ -> i != index }
        val best = mine.maxByOrNull { (road, km) -> km - (others.maxOfOrNull { it[road] ?: 0.0 } ?: 0.0) }
            ?: return null
        val advantage = best.value - (others.maxOfOrNull { it[best.key] ?: 0.0 } ?: 0.0)
        return if (others.isEmpty() || advantage > 0.5) best.key else mine.maxByOrNull { it.value }?.key
    }
}
