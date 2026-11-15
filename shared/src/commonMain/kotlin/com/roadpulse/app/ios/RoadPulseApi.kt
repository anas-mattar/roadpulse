package com.roadpulse.app.ios

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.GeocodingClient
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.i18n.stringsFor
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.RouteChoices
import com.roadpulse.app.util.mathRound
import kotlin.coroutines.cancellation.CancellationException

/** A search hit, in simple types for the iPhone app. */
class SearchResult(val name: String, val detail: String, val lat: Double, val lon: Double)

/** One route choice: its tag ("Fastest", "No tolls", "+5 min"), main road, time, length and tolls. */
class RouteSummary(val tag: String, val via: String?, val minutes: Int, val km: Double, val hasToll: Boolean)

/**
 * What the iPhone app calls: search and route choices, using the same clients and wording as the
 * Android app. Errors reach Swift as thrown errors (network failures, server refusals).
 */
class RoadPulseApi(geocodingUrl: String, routingUrl: String) {
    private val geocoder = GeocodingClient(geocodingUrl)
    private val router = RoutingClient(routingUrl)

    @Throws(Exception::class, CancellationException::class)
    suspend fun search(query: String, nearLat: Double, nearLon: Double): List<SearchResult> =
        geocoder.search(query, GeoPoint(nearLat, nearLon), limit = 8)
            .map { SearchResult(it.name, it.description, it.location.lat, it.location.lon) }

    /** The fastest route and up to two alternatives, described in [language]. */
    @Throws(Exception::class, CancellationException::class)
    suspend fun routes(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, language: GuidanceLanguage): List<RouteSummary> {
        val routes = router.routes(GeoPoint(fromLat, fromLon), GeoPoint(toLat, toLon))
        val choices = RouteChoices.describe(routes, stringsFor(language))
        return routes.mapIndexed { i, r ->
            RouteSummary(choices[i].tag, choices[i].via, mathRound(r.timeSeconds / 60).toInt(), r.lengthKm, r.hasToll)
        }
    }
}
