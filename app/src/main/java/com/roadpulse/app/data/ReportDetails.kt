package com.roadpulse.app.data

/** Optional details per report type; codes match the backend's ReportRules.Subtypes. */
object ReportDetails {
    val subtypes: Map<ReportType, List<Pair<String, String>>> = mapOf(
        ReportType.POLICE to listOf("VISIBLE" to "Visible", "HIDDEN" to "Hidden", "OTHER_SIDE" to "Other side"),
        ReportType.CRASH to listOf("MINOR" to "Minor", "MAJOR" to "Major", "OTHER_SIDE" to "Other side"),
        ReportType.HAZARD to listOf(
            "OBJECT_ON_ROAD" to "Object on road", "POTHOLE" to "Pothole", "STOPPED_VEHICLE" to "Stopped vehicle",
            "ANIMAL" to "Animal", "FLOOD" to "Flood", "CONSTRUCTION" to "Construction",
        ),
        ReportType.TRAFFIC_JAM to listOf("MODERATE" to "Moderate", "HEAVY" to "Heavy", "STANDSTILL" to "Standstill"),
        ReportType.ROAD_CLOSED to listOf("FULL" to "Fully closed", "LANE_BLOCKED" to "Lane blocked", "CONSTRUCTION" to "Construction"),
    )

    /** How long a closure is expected to last (minutes); null = the usual time. */
    val closureDurations: List<Pair<Int?, String>> = listOf(null to "Not sure", 60 to "1 hour", 180 to "3 hours", 1440 to "All day")

    fun label(type: ReportType, subtype: String?): String? = subtypes[type]?.firstOrNull { it.first == subtype }?.second
}
