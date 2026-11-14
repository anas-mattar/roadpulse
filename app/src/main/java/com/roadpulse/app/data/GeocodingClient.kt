package com.roadpulse.app.data

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Place search backed by a Photon server (OpenStreetMap data). */
class GeocodingClient(private val baseUrl: String) {

    suspend fun search(query: String, near: GeoPoint?, limit: Int = 6): List<Place> {
        val url = "$baseUrl/api/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .apply {
                if (near != null) {
                    addQueryParameter("lat", near.lat.toString())
                    addQueryParameter("lon", near.lon.toString())
                }
            }
            .build()
        val body = Http.execute(Request.Builder().url(url).header("User-Agent", "RoadPulse/0.1").build())
        return Http.json.decodeFromString<PhotonResponse>(body).features.mapNotNull { it.toPlace() }
    }

    /** Name of the place at [point] (nearest address or named place), or null if none is known. */
    suspend fun reverse(point: GeoPoint): Place? {
        val url = "$baseUrl/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", point.lat.toString())
            .addQueryParameter("lon", point.lon.toString())
            .addQueryParameter("limit", "1")
            .build()
        val body = Http.execute(Request.Builder().url(url).header("User-Agent", "RoadPulse/0.1").build())
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
