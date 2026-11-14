package com.roadpulse.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

/** Place categories served by the RoadPulse backend (names match its PlaceCategory). */
enum class PlaceCategory(val label: String) {
    FUEL("Fuel"),
    PARKING("Parking"),
    FOOD("Food"),
    EV_CHARGING("EV charging"),
    REST_AREA("R&R"),
    SPEED_CAMERA("Speed camera"),
    SCHOOL_ZONE("School"),
}

/** A place from the backend's OpenStreetMap places, with its distance from the search point. */
@Serializable
data class MapPlace(
    val id: String,
    val category: PlaceCategory,
    val name: String? = null,
    val brand: String? = null,
    val lat: Double,
    val lon: Double,
    val paid: Boolean? = null,
    val maxSpeedKph: Int? = null,
    val distanceM: Double = 0.0,
    /** OpenStreetMap opening_hours text, when mapped. */
    val openingHours: String? = null,
) {
    val location get() = GeoPoint(lat, lon)

    /** As a destination: its name (or brand, or the category), with distance and parking fee. */
    fun toPlace(
        s: com.roadpulse.app.i18n.AppStrings = com.roadpulse.app.i18n.EnglishStrings,
        now: java.time.LocalDateTime = java.time.LocalDateTime.now(),
    ): Place {
        val title = name ?: brand ?: s.category(category)
        val detail = buildList {
            if (distanceM > 0) add(if (distanceM < 1000) s.distance(distanceM.toInt() / 1000.0) else s.distance(distanceM / 1000))
            if (brand != null && brand != title) add(brand)
            when (paid) {
                true -> add(s.paid)
                false -> add(s.free)
                null -> Unit
            }
            OpeningHours.status(openingHours, now)?.let { add(s.openStatus(it)) }
        }.joinToString(" · ")
        return Place(title, detail, location)
    }
}

@Serializable
private data class AlongRouteBody(val category: PlaceCategory, val points: List<List<Double>>, val corridorM: Double)

/** Nearby and along-route place lookups on the RoadPulse backend. */
class PlacesClient(private val baseUrl: String) {

    suspend fun nearby(category: PlaceCategory, near: GeoPoint, radiusM: Int = 5_000, limit: Int = 20): List<MapPlace> {
        val url = "$baseUrl/v1/places/nearby".toHttpUrl().newBuilder()
            .addQueryParameter("category", category.name)
            .addQueryParameter("lat", near.lat.toString())
            .addQueryParameter("lon", near.lon.toString())
            .addQueryParameter("radiusM", radiusM.toString())
            .addQueryParameter("limit", limit.toString())
            .build()
        return Http.json.decodeFromString(ListSerializer(MapPlace.serializer()), Http.execute(Request.Builder().url(url).build()))
    }

    /** Places of [category] within [corridorM] of [shape] (the route line). */
    suspend fun alongRoute(category: PlaceCategory, shape: List<GeoPoint>, corridorM: Double = 40.0): List<MapPlace> {
        val points = thin(shape, MAX_POINTS).map { listOf(it.lat, it.lon) }
        val body = Http.json.encodeToString(AlongRouteBody.serializer(), AlongRouteBody(category, points, corridorM))
        val request = Request.Builder().url("$baseUrl/v1/places/along-route")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        return Http.json.decodeFromString(ListSerializer(MapPlace.serializer()), Http.execute(request))
    }

    companion object {
        /** The server accepts up to 5,000 points; long routes are thinned, keeping both ends. */
        const val MAX_POINTS = 4_000

        fun thin(shape: List<GeoPoint>, max: Int): List<GeoPoint> {
            if (shape.size <= max) return shape
            val step = Math.ceil(shape.size / max.toDouble()).toInt()
            val kept = shape.filterIndexed { i, _ -> i % step == 0 }
            return if (kept.last() == shape.last()) kept else kept + shape.last()
        }
    }
}
