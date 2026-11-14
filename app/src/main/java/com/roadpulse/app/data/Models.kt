package com.roadpulse.app.data

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class Place(val name: String, val description: String, val location: GeoPoint)

@Serializable
data class Maneuver(
    val instruction: String,
    val lengthKm: Double,
    val timeSeconds: Double,
    /** Index into [Route.shape] where this maneuver starts. */
    val beginShapeIndex: Int,
    /** Short spoken heads-up before the maneuver, e.g. "Turn right onto Jalan Ampang." */
    val verbalAlert: String? = null,
    /** Full spoken instruction at the maneuver. */
    val verbalPre: String? = null,
)

@Serializable
data class Route(
    val shape: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val lengthKm: Double,
    val timeSeconds: Double,
)

enum class ReportType(val label: String, val colorHex: String) {
    POLICE("Police", "#1E66F5"),
    CRASH("Crash", "#D20F39"),
    HAZARD("Hazard", "#DF8E1D"),
    TRAFFIC_JAM("Traffic jam", "#E64553"),
    ROAD_CLOSED("Road closed", "#4C4F69"),
}

data class Report(
    val id: String,
    val type: ReportType,
    val location: GeoPoint,
    val createdAtMillis: Long,
    val confirmations: Int = 0,
)
