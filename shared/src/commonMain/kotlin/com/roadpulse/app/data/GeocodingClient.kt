package com.roadpulse.app.data

import kotlinx.serialization.Serializable

/** Place search backed by a Photon server (OpenStreetMap data). */
class GeocodingClient(private val baseUrl: String) {

    /**
     * Places matching [query]. With [near] (the driver's position), more candidates are fetched and
     * the ones nearby come first (see [rankByDistance]); far-away places only when nothing closer matches.
     */
    suspend fun search(query: String, near: GeoPoint?, limit: Int = 6): List<Place> {
        val params = buildList {
            add("q" to query)
            add("limit" to (if (near != null) maxOf(limit * 3, 20) else limit).toString())
            if (near != null) {
                add("lat" to near.lat.toString())
                add("lon" to near.lon.toString())
            }
        }
        val body = Http.execute(Http.url("$baseUrl/api/", *params.toTypedArray()), headers = Http.USER_AGENT)
        val places = Http.json.decodeFromString<PhotonResponse>(body).features.mapNotNull { it.toPlace() }
        return rankByDistance(places, near).take(limit)
    }

    companion object {
        /** Distance bands, nearest first: around town, the region, the country and neighbours, anywhere. */
        val BANDS_KM = listOf(60.0, 400.0, 2500.0)

        /**
         * Puts places within 60 km first, then 400 km, then 2,500 km (all of Malaysia from anywhere in it), then the rest, keeping the search
         * service's order (how well the name matches) inside each band. Pure: unit tested.
         */
        fun rankByDistance(places: List<Place>, near: GeoPoint?): List<Place> {
            if (near == null) return places
            return places.withIndex().sortedWith(compareBy({ (_, p) ->
                val km = Geo.distanceMeters(near, p.location) / 1000
                BANDS_KM.indexOfFirst { km <= it }.let { if (it < 0) BANDS_KM.size else it }
            }, { it.index })).map { it.value }
        }
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
