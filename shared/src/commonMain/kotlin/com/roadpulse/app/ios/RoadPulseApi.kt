package com.roadpulse.app.ios

import com.roadpulse.app.data.DeviceApi
import com.roadpulse.app.data.DeviceTokenStore
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.GeocodingClient
import com.roadpulse.app.data.Http
import com.roadpulse.app.data.MapArea
import com.roadpulse.app.data.PlaceCategory
import com.roadpulse.app.data.PlacesClient
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportFeed
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.ReportsClient
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RouteOptions
import com.roadpulse.app.data.sixDecimals
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.data.TollClient
import com.roadpulse.app.data.TrafficClient
import com.roadpulse.app.data.Vehicle
import com.roadpulse.app.i18n.stringsFor
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.RouteChoices
import com.roadpulse.app.util.mathRound
import kotlin.coroutines.cancellation.CancellationException

/** The phone's own store for the anonymous device token. */
expect fun tokenStoreFor(baseUrl: String): DeviceTokenStore

/** A search hit, in simple types for the iPhone app. */
class SearchResult(val name: String, val detail: String, val lat: Double, val lon: Double)

/** One route choice: its tag ("Fastest", "No tolls", "+5 min"), main road, time, length and tolls. */
class RouteSummary(
    val tag: String, val via: String?, val minutes: Int, val km: Double, val hasToll: Boolean,
    /** The route line, for drawing on the map. */
    val shape: List<GeoPoint>,
    /** The full route, handed back to [DriveController.start]. */
    val route: Route,
    /** "Toll ~RM 6.80", "Toll road"…, or null for a toll-free route. */
    val tollLabel: String?,
    /** Live traffic: slow and jammed stretches (fractions of the route), their lines on the map, and the extra minutes. */
    val trafficPieces: List<com.roadpulse.app.nav.TrafficPiece> = emptyList(),
    val trafficLines: List<TrafficPolyline> = emptyList(),
    val trafficDelayMinutes: Int = 0,
) {
    /** Minutes including live traffic. */
    val minutesWithTraffic: Int get() = minutes + trafficDelayMinutes
}

/** Route summaries from routes (no toll estimate), e.g. for "Routes" during a drive. */
internal fun summarize(routes: List<Route>, language: GuidanceLanguage): List<RouteSummary> {
    val strings = stringsFor(language)
    val choices = RouteChoices.describe(routes, strings)
    return routes.mapIndexed { i, r ->
        RouteSummary(choices[i].tag, choices[i].via, mathRound(r.timeSeconds / 60).toInt(), r.lengthKm, r.hasToll,
            r.shape, r, TollClient.label(r.hasToll, null, strings))
    }
}

/** The coloured stretches of [shape] as map lines. */
internal fun trafficLinesFor(shape: List<GeoPoint>, pieces: List<com.roadpulse.app.nav.TrafficPiece>): List<TrafficPolyline> =
    com.roadpulse.app.nav.RouteTraffic.polylines(shape, pieces).map { (level, pts) -> TrafficPolyline(level, pts) }

/** A community report on the map. [typeName] is a [ReportType] name; [label] is in the app language. */
class ReportPin(
    val id: String, val typeName: String, val label: String, val detail: String,
    val colorHex: String, val lat: Double, val lon: Double, val own: Boolean,
)

/** A slow (SLOW) or jammed (JAM) piece of road on the traffic layer. */
class TrafficPolyline(val level: String, val points: List<GeoPoint>)

/** A place from the RoadPulse server (fuel, parking…), described in the app language. */
/** The driver's profile, ready to show. */
class ProfileCard(
    val nickname: String, val levelName: String, val points: String, val progress: Float, val toNext: String,
    val reports: Int, val confirmed: Int, val votes: Int, val badges: List<BadgeItem>,
)

class BadgeItem(val label: String, val earned: Boolean)

class PlaceResult(val name: String, val detail: String, val lat: Double, val lon: Double)

/**
 * What the iPhone app calls: search, routes, reports, places, tolls and traffic, using the same
 * clients and wording as the Android app. [backendUrl] is empty when there is no RoadPulse server
 * (the laptop's server is off in Settings): those features then return nothing.
 * Errors reach Swift as thrown errors.
 */
class RoadPulseApi(geocodingUrl: String, routingUrl: String, private val backendUrl: String = "") {
    private val geocoder = GeocodingClient(geocodingUrl)
    private val router = RoutingClient(routingUrl)
    private val device = backendUrl.takeIf { it.isNotEmpty() }?.let { DeviceApi(it, tokenStoreFor(it)) }
    private val reports = device?.let { ReportsClient(backendUrl, it) }

    val hasServer: Boolean get() = backendUrl.isNotEmpty()

    /** Name of the place at a long-pressed point ("Dropped pin" with coordinates when none is known). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun placeAt(lat: Double, lon: Double, language: GuidanceLanguage): SearchResult {
        val named = runCatching { geocoder.reverse(GeoPoint(lat, lon)) }.getOrNull()
        return if (named != null) SearchResult(named.name, named.description, lat, lon)
        else SearchResult(stringsFor(language).droppedPin, "${sixDecimals(lat)}, ${sixDecimals(lon)}", lat, lon)
    }

    private val community = device?.let { com.roadpulse.app.data.CommunityPlacesClient(it) }

    /** OpenStreetMap places and places drivers added (with the laptop server), nearest first. */
    @Throws(Exception::class, CancellationException::class)
    suspend fun search(query: String, nearLat: Double, nearLon: Double, language: GuidanceLanguage): List<SearchResult> =
        com.roadpulse.app.data.PlaceSearch.search(geocoder, community, query, GeoPoint(nearLat, nearLon), stringsFor(language), limit = 8,
            directory = backendUrl.takeIf { it.isNotEmpty() }?.let { com.roadpulse.app.data.DirectoryClient(it) })
            .map { SearchResult(it.name, it.description, it.location.lat, it.location.lon) }

    /** Kinds for "Add a missing place": (name, label in the app language). */
    fun placeKinds(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return com.roadpulse.app.data.CommunityPlaceKind.entries.map { listOf(it.name, s.placeKind(it)) }
    }

    /** Adds a place search does not know; returns false when the server refused or could not be reached. */
    @Throws(Exception::class, CancellationException::class)
    suspend fun addPlace(name: String, kindName: String, lat: Double, lon: Double, note: String): Boolean {
        val client = community ?: return false
        val kind = com.roadpulse.app.data.CommunityPlaceKind.entries.firstOrNull { it.name == kindName } ?: return false
        return runCatching { client.add(name, kind, GeoPoint(lat, lon), note) }.isSuccess
    }

    /**
     * The fastest route and up to two alternatives via [stops] (in order), planned with [options]
     * (vehicle, roads to avoid), described in [language], with toll estimates.
     */
    @Throws(Exception::class, CancellationException::class)
    suspend fun routes(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, stops: List<SearchResult>,
                       options: RouteOptions, language: GuidanceLanguage): List<RouteSummary> {
        val routes = router.routes(GeoPoint(fromLat, fromLon), GeoPoint(toLat, toLon),
            stops = stops.map { GeoPoint(it.lat, it.lon) }, options = options)
        val strings = stringsFor(language)
        val choices = RouteChoices.describe(routes, strings)
        val traffic = com.roadpulse.app.nav.RouteTraffic.forRoutes(backendUrl, routes)
        return routes.mapIndexed { i, r ->
            val toll = if (r.hasToll && hasServer) {
                runCatching { TollClient(backendUrl).estimate(r.shape, options.vehicle) }.getOrNull()
            } else null
            val info = traffic.getOrNull(i)
            RouteSummary(choices[i].tag, choices[i].via, mathRound(r.timeSeconds / 60).toInt(), r.lengthKm, r.hasToll,
                r.shape, r, TollClient.label(r.hasToll, toll, strings),
                trafficPieces = info?.pieces.orEmpty(),
                trafficLines = trafficLinesFor(r.shape, info?.pieces.orEmpty()),
                trafficDelayMinutes = mathRound((info?.delaySeconds ?: 0.0) / 60).toInt())
        }
    }

    /** True when the RoadPulse server answers (Settings > Test connection). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun serverReachable(): Boolean =
        hasServer && runCatching { Http.execute("$backendUrl/health").contains("ok") }.getOrDefault(false)

    /**
     * Active reports in the box, for the map. They are also shared with the drive in progress,
     * which warns about reports on the route ahead.
     */
    @Throws(Exception::class, CancellationException::class)
    suspend fun reportsIn(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double, language: GuidanceLanguage): List<ReportPin> {
        val client = reports ?: return emptyList()
        val area = MapArea.subscriptionFor(MapArea(minLon, minLat, maxLon, maxLat))
        val list = client.list(area)
        ReportFeed.reports.value = list
        return list.map { pin(it, language) }
    }

    /** Sends a report at the given position. */
    @Throws(Exception::class, CancellationException::class)
    /** Sends a report; [subtype] is a code from [reportDetails] or null, [durationMinutes] -1 for the usual time. */
    suspend fun report(typeName: String, lat: Double, lon: Double, subtype: String?, durationMinutes: Int, language: GuidanceLanguage): ReportPin? {
        val client = reports ?: return null
        val r = client.submit(ReportType.valueOf(typeName), GeoPoint(lat, lon), subtype, durationMinutes.takeIf { it > 0 })
        OwnReports.ids += r.id
        return pin(r, language)
    }

    /** "Still there" (true) or "Not there" (false). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun vote(reportId: String, stillThere: Boolean) {
        reports?.vote(reportId, stillThere)
    }

    /** Places of one category near a point, nearest first, with "Open · closes 22:00" when known. */
    @Throws(Exception::class, CancellationException::class)
    suspend fun places(categoryName: String, nearLat: Double, nearLon: Double, language: GuidanceLanguage,
                       dayIndex: Int, hour: Int, minute: Int): List<PlaceResult> {
        if (!hasServer) return emptyList()
        val strings = stringsFor(language)
        return PlacesClient(backendUrl).nearby(PlaceCategory.valueOf(categoryName), GeoPoint(nearLat, nearLon), radiusM = 10_000, limit = 20)
            .map { p ->
                val open = com.roadpulse.app.data.OpeningHours.status(p.openingHours, dayIndex, hour, minute)
                val place = p.describe(strings, open)
                PlaceResult(place.name, place.description, p.lat, p.lon)
            }
    }

    /** Slow and jammed roads in the box (live traffic; free-flowing roads are left out). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun traffic(minLat: Double, minLon: Double, maxLat: Double, maxLon: Double): List<TrafficPolyline> {
        if (!hasServer || maxLat - minLat > 0.9 || maxLon - minLon > 0.9) return emptyList()
        return TrafficClient(backendUrl).lines(minLat, minLon, maxLat, maxLon)
            .filter { it.level != "FREE" }
            .map { l -> TrafficPolyline(l.level, l.points.map { GeoPoint(it[0], it[1]) }) }
    }

    /** Category buttons under the search box: (category name, label in the app language). */
    fun categories(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return listOf(PlaceCategory.FUEL, PlaceCategory.PARKING, PlaceCategory.FOOD, PlaceCategory.EV_CHARGING, PlaceCategory.REST_AREA)
            .map { listOf(it.name, s.category(it)) }
    }

    /** Report types with their names in the app language: (type name, label, colour). */
    fun reportTypes(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return ReportType.entries.map { listOf(it.name, s.reportType(it), it.colorHex) }
    }

    /** Optional details for a report type: (code, label). Empty when the type has none. */
    fun reportDetails(typeName: String, language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        val type = ReportType.valueOf(typeName)
        return com.roadpulse.app.data.ReportDetails.subtypes[type].orEmpty().map { (code, label) -> listOf(code, s.reportDetail(type, code) ?: label) }
    }

    /** "How long?" choices for a closure: (minutes, or "" for not sure; label). */
    fun closureDurations(language: GuidanceLanguage): List<List<String>> {
        val s = stringsFor(language)
        return com.roadpulse.app.data.ReportDetails.closureDurations.map { (minutes, _) -> listOf(minutes?.toString() ?: "", s.closureDuration(minutes)) }
    }

    // ------------------------------------------------------------ Profile and privacy

    private val profileClient = device?.let { com.roadpulse.app.data.ProfileClient(it) }
    private val privacyClient = device?.let { com.roadpulse.app.data.PrivacyClient(it) }

    /** Points, level and badges for this phone (null without the laptop server). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun profile(language: GuidanceLanguage): ProfileCard? = profileClient?.get()?.let { card(it, language) }

    /** Sets the nickname (empty text removes it). */
    @Throws(Exception::class, CancellationException::class)
    suspend fun setNickname(name: String, language: GuidanceLanguage): ProfileCard? =
        profileClient?.setNickname(name.trim().ifEmpty { null })?.let { card(it, language) }

    /** Everything the server holds about this phone, as JSON text. */
    @Throws(Exception::class, CancellationException::class)
    suspend fun exportMyData(): String = privacyClient?.export() ?: "{}"

    /** Deletes this phone's reports, votes, profile and shares on the server, and forgets the device token. */
    @Throws(Exception::class, CancellationException::class)
    suspend fun deleteMyData() {
        privacyClient?.deleteAll()
        tokenStoreFor(backendUrl).save(null)
        OwnReports.ids.clear()
    }

    private fun card(p: com.roadpulse.app.data.Profile, language: GuidanceLanguage): ProfileCard {
        val s = stringsFor(language)
        val levels = listOf("LEARNER", "ROAD_SCOUT", "NAVIGATOR", "ROAD_GUARDIAN", "ROAD_LEGEND")
        val next = levels.getOrNull(p.levelIndex + 1)
        val toNext = p.nextLevelPoints?.let { n -> if (next != null) s.pointsToNext(n - p.points, s.levelName(next)) else null } ?: s.topLevel
        return ProfileCard(
            nickname = p.nickname ?: "",
            levelName = s.levelName(p.level),
            points = s.points(p.points),
            progress = p.progress(),
            toNext = toNext,
            reports = p.stats.reports,
            confirmed = p.stats.confirmationsReceived,
            votes = p.stats.votes,
            badges = com.roadpulse.app.data.Profile.ALL_BADGES.map { BadgeItem(s.badge(it), it in p.badges) },
        )
    }

    private fun pin(r: Report, language: GuidanceLanguage): ReportPin {
        val s = stringsFor(language)
        val detail = r.subtype?.let { s.reportDetail(r.type, it) }
        val info = listOfNotNull(detail, if (r.confirmations > 0) s.confirmations(r.confirmations) else null).joinToString(" · ")
        return ReportPin(r.id, r.type.name, s.reportType(r.type), info, r.type.colorHex, r.location.lat, r.location.lon, r.id in OwnReports.ids)
    }
}
