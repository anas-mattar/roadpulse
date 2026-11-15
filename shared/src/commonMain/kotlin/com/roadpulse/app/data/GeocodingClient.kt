package com.roadpulse.app.data

import kotlinx.serialization.Serializable

/** Place search backed by a Photon server (OpenStreetMap data). */
class GeocodingClient(private val baseUrl: String) {

    suspend fun search(query: String, near: GeoPoint?, limit: Int = 6): List<Place> {
        val params = buildList {
            add("q" to query)
            add("limit" to limit.toString())
            if (near != null) {
                add("lat" to near.lat.toString())
                add("lon" to near.lon.toString())
            }
        }
        val body = Http.execute(Http.url("$baseUrl/api/", *params.toTypedArray()), headers = Http.USER_AGENT)
        return Http.json.decodeFromString<PhotonResponse>(body).features.mapNotNull { it.toPlace() }
    }

    /** Name of the place at [point] (nearest address or named place), or null if none is known. */
    suspend fun reverse(point: GeoPoint): Place? {
        val url = Http.url("$baseUrl/reverse", "lat" to point.lat.toString(), "lon" to point.lon.toString(), "limit" to "1")
        val body = Http.execute(url, headers = Http.USER_AGENT)
        // Keep the pressed point itself: the address may be a few metres away.
        return Http.json.decodeFromString<PhotonResponse>(body).features.firstOrNull()?.toPlace()?.copy(location = point)
    }

    @Serializable
    private data class PhotonResponse(val features: List<Feature> = emptyList())

    @Serializable
    private data class Feature(val geometry: Geometry, val properties: Properties = Properties()) {
        fun toPlace(): Place? {
            if (geometry.coordinates.size < 2) return null
            val p = properties
            val title = p.name ?: listOfNotNull(p.housenumber, p.street).joinToString(" ").ifBlank { return null }
            val detail = listOfNotNull(p.street.takeIf { p.name != null }, p.city, p.state, p.country)
                .distinct().joinToString(", ")
            return Place(title, detail, GeoPoint(lat = geometry.coordinates[1], lon = geometry.coordinates[0]))
        }
    }

    @Serializable
    private data class Geometry(val coordinates: List<Double> = emptyList())

    @Serializable
    private data class Properties(
        val name: String? = null,
        val street: String? = null,
        val housenumber: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
    )
}
