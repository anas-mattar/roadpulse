package com.roadpulse.app.data

import com.roadpulse.app.i18n.AppStrings
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable

/** Kinds of places drivers can add (same names as the server's CommunityPlaceKind). */
enum class CommunityPlaceKind {
    SHOP, MALL, FOOD, CAFE, CLINIC, HOSPITAL, OFFICE, HOTEL, RESIDENCE, BUILDING, ENTRANCE, PARKING, FUEL, SCHOOL, WORSHIP, BANK, GYM, OTHER,
}

/** A place a driver added; [status] is Pending (only its creator sees it), Approved or Rejected. */
@Serializable
data class CommunityPlace(
    val id: String,
    val name: String,
    val kind: CommunityPlaceKind,
    val note: String? = null,
    val lat: Double,
    val lon: Double,
    val distanceM: Double = 0.0,
    val status: String = "Pending",
    val own: Boolean = false,
) {
    val pending: Boolean get() = status == "Pending"

    /** As a search result: "Clinic · Added by drivers · Level 2" or "… · Waiting for review". */
    fun toPlace(s: AppStrings): Place {
        val detail = listOfNotNull(
            s.placeKind(kind),
            if (pending) s.waitingForReview else s.addedByDrivers,
            note?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        return Place(name, detail, GeoPoint(lat, lon))
    }
}

/** "Add a missing place" on the RoadPulse server (needs the device token). */
class CommunityPlacesClient(private val api: DeviceApi) {
    suspend fun add(name: String, kind: CommunityPlaceKind, at: GeoPoint, note: String?): CommunityPlace {
        val body = Http.json.encodeToString(AddBody.serializer(), AddBody(name.trim(), kind, at.lat, at.lon, note?.trim()?.ifEmpty { null }))
        return Http.json.decodeFromString(CommunityPlace.serializer(), api.call("POST", "/v1/places/community", body))
    }

    /** Places whose name contains [query] near [near]: approved ones, plus this device's own. */
    suspend fun search(query: String, near: GeoPoint, limit: Int = 10): List<CommunityPlace> {
        val path = Http.url("/v1/places/community/search", "q" to query, "lat" to near.lat.toString(), "lon" to near.lon.toString(), "limit" to limit.toString())
        return Http.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CommunityPlace.serializer()), api.call("GET", path))
    }

    suspend fun remove(id: String) {
        api.call("DELETE", "/v1/places/community/$id")
    }

    @Serializable private data class AddBody(val name: String, val kind: CommunityPlaceKind, val lat: Double, val lon: Double, val note: String?)
}

/** What a driver says is wrong with a place from search (same names as the server's PlaceProblemKind). */
enum class PlaceProblemKind { WRONG_POSITION, CLOSED, NOT_THERE }

/** "Report a problem with this place" on the server (needs the device token). A moderator checks each report. */
class PlaceProblemsClient(private val api: DeviceApi) {
    /** [at] is where the place really is (for WRONG_POSITION), when the driver knows it. */
    suspend fun report(placeId: String, kind: PlaceProblemKind, at: GeoPoint?, note: String?) {
        val body = Http.json.encodeToString(Body.serializer(), Body(placeId, kind, at?.lat, at?.lon, note?.trim()?.ifEmpty { null }))
        api.call("POST", "/v1/places/problems", body)
    }

    /** A wrong position needs the right position or a note saying where the place is. Pure: tested. */
    companion object {
        fun canSend(kind: PlaceProblemKind?, hasPosition: Boolean, note: String): Boolean =
            kind != null && (kind != PlaceProblemKind.WRONG_POSITION || hasPosition || note.trim().length >= 3)
    }

    @Serializable private data class Body(val placeId: String, val kind: PlaceProblemKind, val lat: Double?, val lon: Double?, val note: String?)
}

/** A named place from Overture Maps on the RoadPulse server (shops, clinics, condos…). */
@Serializable
data class DirectoryPlace(
    val id: String,
    val name: String,
    val category: String? = null,
    val address: String? = null,
    val lat: Double,
    val lon: Double,
    val distanceM: Double = 0.0,
) {
    /** "Clinic · Jalan Ampang, Kuala Lumpur" (no kind when Overture's category is unclear or wrong). */
    fun toPlace(s: AppStrings): Place {
        val kind = OvertureCategories.kind(category, name)?.let(s::placeKind)
        return Place(name, listOfNotNull(kind, address?.takeIf { it.isNotBlank() }).joinToString(" · "), GeoPoint(lat, lon), directoryId = id)
    }
}

/**
 * Turns Overture's 2,000+ categories into the short list drivers see (the same kinds as "Add a
 * missing place"), and corrects common mistakes from the name: a condo labelled "historic site"
 * becomes Residence. Unknown or doubtful categories show no kind at all. Pure: unit tested.
 */
object OvertureCategories {
    private val byCategory: List<Pair<CommunityPlaceKind, List<String>>> = listOf(
        CommunityPlaceKind.MALL to listOf("shopping_mall", "shopping_center", "outlet_mall"),
        CommunityPlaceKind.HOSPITAL to listOf("hospital"),
        CommunityPlaceKind.CLINIC to listOf("dentist", "dental", "clinic", "doctor", "medical", "pharmacy", "physician", "health"),
        CommunityPlaceKind.CAFE to listOf("cafe", "coffee", "tea_room", "bubble_tea", "juice"),
        CommunityPlaceKind.FOOD to listOf("restaurant", "food", "bakery", "eatery", "diner", "bistro", "noodle", "burger", "pizza", "bar_and_grill", "hawker"),
        CommunityPlaceKind.HOTEL to listOf("hotel", "motel", "resort", "hostel", "lodging", "guest_house", "holiday_rental", "bed_and_breakfast", "serviced_apartment"),
        CommunityPlaceKind.RESIDENCE to listOf("real_estate", "apartment", "condominium", "residential", "housing"),
        CommunityPlaceKind.FUEL to listOf("gas_station", "fueling_station", "fuel"),
        CommunityPlaceKind.PARKING to listOf("parking"),
        CommunityPlaceKind.SCHOOL to listOf("school", "college", "university", "kindergarten", "education", "tuition"),
        CommunityPlaceKind.WORSHIP to listOf("mosque", "church", "temple", "place_of_worship", "religious", "gurdwara"),
        CommunityPlaceKind.BANK to listOf("bank", "atm", "credit_union"),
        CommunityPlaceKind.GYM to listOf("gym", "fitness", "yoga", "martial_arts"),
        CommunityPlaceKind.OFFICE to listOf("office", "professional_service", "corporate", "consultant", "agency", "accountant", "lawyer", "it_service", "engineering"),
        CommunityPlaceKind.SHOP to listOf("store", "shop", "retail", "market", "boutique", "supermarket", "grocery", "convenience", "dealer"),
        CommunityPlaceKind.BUILDING to listOf("building", "tower"),
    )

    /** Categories Overture often gets wrong for ordinary buildings: trust the name instead. */
    private val doubtful = listOf("historic", "landmark", "monument", "event", "structure_and_geography", "public_and_government")

    private val byName: List<Pair<CommunityPlaceKind, Regex>> = listOf(
        CommunityPlaceKind.RESIDENCE to Regex("\\b(residen\\w*|soho|suites?|apartments?|condo\\w*|kondominium|pangsapuri)\\b"),
        CommunityPlaceKind.HOTEL to Regex("\\b(hotel|inn|hostel)\\b"),
        CommunityPlaceKind.CLINIC to Regex("\\b(klinik|clinic|dental|pergigian)\\b"),
        CommunityPlaceKind.HOSPITAL to Regex("\\b(hospital)\\b"),
        CommunityPlaceKind.WORSHIP to Regex("\\b(masjid|surau|mosque|church|gereja|temple|kuil|tokong)\\b"),
        CommunityPlaceKind.MALL to Regex("\\b(mall)\\b"),
        CommunityPlaceKind.SCHOOL to Regex("\\b(sekolah|school|kolej|college|universiti|university|tadika)\\b"),
        CommunityPlaceKind.OFFICE to Regex("\\b(sdn\\.? ?bhd|berhad|bhd)\\b"),
    )

    fun kind(category: String?, name: String): CommunityPlaceKind? {
        val c = category?.lowercase().orEmpty()
        val fromCategory = if (c.isEmpty() || doubtful.any { c.contains(it) }) null
        else byCategory.firstOrNull { (_, words) -> words.any { c.contains(it) } }?.first
        if (fromCategory != null) return fromCategory
        val n = name.lowercase()
        return byName.firstOrNull { (_, re) -> re.containsMatchIn(n) }?.first
    }
}

/** Overture Maps places search on the RoadPulse server (public, no device token). */
class DirectoryClient(private val baseUrl: String) {
    suspend fun search(query: String, near: GeoPoint, limit: Int = 10): List<DirectoryPlace> {
        val url = Http.url("$baseUrl/v1/places/search", "q" to query, "lat" to near.lat.toString(), "lon" to near.lon.toString(), "limit" to limit.toString())
        return Http.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(DirectoryPlace.serializer()), Http.execute(url, headers = Http.USER_AGENT))
    }
}

/**
 * Search used by both apps: places drivers added, Overture Maps places (both from the RoadPulse
 * server) and OpenStreetMap places (Photon), one list, nearest first. A failing server only drops
 * its part; duplicates (same name within 50 m) are shown once.
 */
object PlaceSearch {
    /** The Jalanna server gets this long to answer a search; after that its part is left out. */
    const val SERVER_BUDGET_MS = 4000L
    /** When the server already found places, the OpenStreetMap search gets this much longer (it is often the slow one). */
    const val OSM_GRACE_MS = 1500L

    suspend fun search(
        geocoder: GeocodingClient, community: CommunityPlacesClient?, query: String, near: GeoPoint?, s: AppStrings, limit: Int,
        directory: DirectoryClient? = null, serverBudgetMs: Long = SERVER_BUDGET_MS, osmGraceMs: Long = OSM_GRACE_MS,
    ): List<Place> = kotlinx.coroutines.coroutineScope {
        val usable = near != null && query.count { it.isLetterOrDigit() } >= 2
        // All three searches run at the same time; a slow or failing one must not hold up the list.
        val added = if (community != null && usable) {
            async {
                kotlinx.coroutines.withTimeoutOrNull(serverBudgetMs) {
                    runCatching { community.search(query, near!!) }.getOrDefault(emptyList())
                }.orEmpty().map { it.toPlace(s) }
            }
        } else null
        val listed = if (directory != null && usable) {
            async {
                kotlinx.coroutines.withTimeoutOrNull(serverBudgetMs) {
                    runCatching { directory.search(query, near!!) }.getOrDefault(emptyList())
                }.orEmpty().map { it.toPlace(s) }
            }
        } else null
        val osmSearch = async { runCatching { geocoder.search(query, near, limit) } }
        val fromServer = added?.await().orEmpty() + listed?.await().orEmpty()
        val osm = if (fromServer.isEmpty()) {
            osmSearch.await().getOrThrow()   // the only source: wait for it, and report its failure
        } else {
            val result = kotlinx.coroutines.withTimeoutOrNull(osmGraceMs) { osmSearch.await() }
            if (result == null) osmSearch.cancel()
            result?.getOrDefault(emptyList()).orEmpty()
        }
        // Drivers' places first inside each distance band (they were added because search missed them).
        val all = fromServer + osm
        val unique = mutableListOf<Place>()
        for (p in all) if (unique.none { sameSpot(it, p) }) unique += p
        GeocodingClient.rankByDistance(unique, near).take(limit)
    }

    /** Lower-case letters and digits only (as the server does): "M-City" and "m city" match. */
    fun key(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** The same place from two sources: same name within 100 m, or one name starts with the other within 40 m. */
    fun sameSpot(a: Place, b: Place): Boolean {
        val m = Geo.distanceMeters(a.location, b.location)
        if (m > 100) return false
        val ka = key(a.name)
        val kb = key(b.name)
        return ka == kb || (m <= 40 && minOf(ka.length, kb.length) >= 6 && (ka.startsWith(kb) || kb.startsWith(ka)))
    }
}
