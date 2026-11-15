package com.roadpulse.app.data

import com.roadpulse.app.i18n.AppStrings
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable

/** Kinds of places drivers can add (same names as the server's CommunityPlaceKind). */
enum class CommunityPlaceKind { SHOP, FOOD, CAFE, CLINIC, OFFICE, HOTEL, RESIDENCE, BUILDING, ENTRANCE, PARKING, OTHER }

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

/**
 * Search used by both apps: OpenStreetMap places (Photon) and places drivers added, one list,
 * nearest first. A failing server only drops the drivers' places.
 */
object PlaceSearch {
    suspend fun search(
        geocoder: GeocodingClient, community: CommunityPlacesClient?, query: String, near: GeoPoint?, s: AppStrings, limit: Int,
    ): List<Place> = kotlinx.coroutines.coroutineScope {
        val added = if (community != null && near != null && query.count { it.isLetterOrDigit() } >= 2) {
            async { runCatching { community.search(query, near) }.getOrDefault(emptyList()).map { it.toPlace(s) } }
        } else null
        val osm = geocoder.search(query, near, limit)
        val mine = added?.await().orEmpty()
        // Drivers' places first inside each distance band (they were added because search missed them).
        val merged = (mine + osm.filterNot { o -> mine.any { m -> m.name.equals(o.name, true) && Geo.distanceMeters(m.location, o.location) < 50 } })
        GeocodingClient.rankByDistance(merged, near).take(limit)
    }
}
