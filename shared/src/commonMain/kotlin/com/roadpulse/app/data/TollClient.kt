package com.roadpulse.app.data

import kotlinx.serialization.Serializable

/** One toll plaza on a route, as priced by the backend from the owner's toll table. */
@Serializable
data class TollPlaza(
    val name: String? = null,
    val highway: String? = null,
    /** PRICED, NO_PRICE, CLOSED_SYSTEM or NOT_IN_TABLE (see the backend's TollPlazaStatus). */
    val status: String,
    val priceRm: Double? = null,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)

/** Estimated toll for one route. [totalRm] counts priced plazas only; [complete] when all are priced. */
@Serializable
data class TollEstimate(
    val currency: String = "MYR",
    val totalRm: Double = 0.0,
    val complete: Boolean = true,
    val plazas: List<TollPlaza> = emptyList(),
    val ratesCheckedOn: String? = null,
)

@Serializable
private data class TollEstimateBody(val points: List<List<Double>>, val vehicle: String, val corridorM: Double)

/** Toll estimates from the RoadPulse backend (POST /v1/tolls/estimate). */
class TollClient(private val baseUrl: String) {

    suspend fun estimate(shape: List<GeoPoint>, vehicle: Vehicle): TollEstimate {
        val points = PlacesClient.thin(shape, PlacesClient.MAX_POINTS).map { listOf(it.lat, it.lon) }
        val body = Http.json.encodeToString(TollEstimateBody.serializer(), TollEstimateBody(points, tollClass(vehicle), 40.0))
        return Http.json.decodeFromString(TollEstimate.serializer(), Http.execute("$baseUrl/v1/tolls/estimate", "POST", body))
    }

    companion object {
        /** Taxis and ride-hailing cars pay the car (Class 1) price. */
        fun tollClass(vehicle: Vehicle) = if (vehicle == Vehicle.MOTORCYCLE) "MOTORCYCLE" else "CAR"

        /**
         * What a route card says about tolls, or null for a toll-free route:
         * "Toll ~RM 6.80", "Toll RM 5.00 + ?" (some plazas not priced), "Toll: price unknown",
         * or "Toll road" while the estimate is loading or unavailable.
         */
        fun label(hasToll: Boolean, estimate: TollEstimate?, s: com.roadpulse.app.i18n.AppStrings = com.roadpulse.app.i18n.EnglishStrings): String? {
            if (estimate == null) return if (hasToll) s.tollRoad else null
            if (estimate.plazas.isEmpty()) return if (hasToll) s.tollPriceUnknown else null
            val rm = "RM " + com.roadpulse.app.util.twoDecimals(estimate.totalRm)
            return when {
                estimate.complete -> s.tollAbout(rm)
                estimate.totalRm > 0 -> s.tollAtLeast(rm)
                else -> s.tollPriceUnknown
            }
        }
    }
}
