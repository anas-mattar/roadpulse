package com.roadpulse.app.data

import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class Place(
    val name: String, val description: String, val location: GeoPoint,
    /** Set for places from the server's Overture data: drivers can report a problem with these. */
    val directoryId: String? = null,
)

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
    /** Lanes on the approach to this maneuver, left to right (empty when the map has none). */
    val lanes: List<Lane> = emptyList(),
)

/**
 * One lane before a maneuver. Each field is a set of directions as bit flags (see [Lane.Companion]):
 * [directions] = the arrows painted on the lane, [active] = the direction to take when this is
 * the lane to be in, [valid] = it also works but is not preferred. Values from the routing server.
 */
@Serializable
data class Lane(val directions: Int, val valid: Int = 0, val active: Int = 0) {
    /** The lane the driver should use. */
    val recommended: Boolean get() = active != 0
    /** Also leads to the maneuver. */
    val usable: Boolean get() = active != 0 || valid != 0

    /** The arrow to draw: the direction being taken in this lane, else its first painted arrow. */
    val arrow: Int get() = lowestBit(if (active != 0) active else if (valid != 0) valid else directions)

    companion object {
        const val NONE = 1
        const val THROUGH = 2
        const val SHARP_LEFT = 4
        const val LEFT = 8
        const val SLIGHT_LEFT = 16
        const val SLIGHT_RIGHT = 32
        const val RIGHT = 64
        const val SHARP_RIGHT = 128
        const val REVERSE = 256
        const val MERGE_TO_LEFT = 512
        const val MERGE_TO_RIGHT = 1024

        private fun lowestBit(v: Int) = if (v == 0) THROUGH else v.takeLowestOneBit()

        /** Lanes worth showing: at least two lanes and at least one recommended or usable. */
        fun worthShowing(lanes: List<Lane>) = lanes.size >= 2 && lanes.any { it.usable } && !lanes.all { it.usable }
    }
}

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

/** Maneuver type codes beyond the routing server's own. */
object ManeuverTypes {
    /** A stop on the way was reached (added by the app when joining route legs). */
    const val STOP_REACHED = 100
}
