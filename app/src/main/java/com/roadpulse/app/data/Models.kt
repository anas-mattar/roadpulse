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
    /** Routing-server maneuver type (Valhalla codes, e.g. 10 = right, 26 = enter roundabout). */
    val type: Int = 0,
    /** Road names after the maneuver, first is the main one. */
    val streetNames: List<String> = emptyList(),
    /** Compass heading after the maneuver, degrees clockwise from north. */
    val bearingAfter: Int? = null,
    val roundaboutExitCount: Int? = null,
    /** Signposted exit number, e.g. "608". */
    val exitNumber: String? = null,
    /** Signposted route numbers of the branch taken, e.g. "E37". */
    val exitBranch: List<String> = emptyList(),
    /** Signposted destinations, e.g. "Putrajaya", "Cyberjaya". */
    val exitToward: List<String> = emptyList(),
)

@Serializable
data class Route(
    val shape: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val lengthKm: Double,
    val timeSeconds: Double,
    val hasToll: Boolean = false,
    val hasHighway: Boolean = false,
    /** Stops on the way, in order (not counting the destination). */
    val stops: List<GeoPoint> = emptyList(),
    /** Shape index where each stop is reached, same order as [stops]. */
    val stopShapeIndexes: List<Int> = emptyList(),
)

enum class ReportType(val label: String, val colorHex: String) {
    POLICE("Police", "#1E66F5"),
    CRASH("Crash", "#D20F39"),
    HAZARD("Hazard", "#DF8E1D"),
    TRAFFIC_JAM("Traffic jam", "#E8590C"),
    ROAD_CLOSED("Road closed", "#4C4F69"),
}

/** Why a driver flags a report as abuse (matches the backend's FlagReason). */
enum class FlagReason(val label: String) {
    FAKE("Fake report"),
    SPAM("Spam"),
    OFFENSIVE("Offensive"),
    WRONG_LOCATION("Wrong location"),
}

data class Report(
    val id: String,
    val type: ReportType,
    val location: GeoPoint,
    val createdAtMillis: Long,
    val confirmations: Int = 0,
    val dismissals: Int = 0,
    /** Optional detail, e.g. "POTHOLE" (see ReportDetails). */
    val subtype: String? = null,
    /** When the server will drop it (null for reports kept only on this phone). */
    val expiresAtMillis: Long? = null,
)
