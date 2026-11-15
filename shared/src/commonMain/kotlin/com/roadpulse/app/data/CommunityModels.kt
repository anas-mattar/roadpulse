package com.roadpulse.app.data

import kotlinx.serialization.Serializable

@Serializable
data class ProfileStats(val reports: Int = 0, val confirmationsReceived: Int = 0, val votes: Int = 0, val removed: Int = 0)

/** Points, level and badges for this device (backend: Modules/Profile). */
@Serializable
data class Profile(
    val nickname: String? = null,
    val points: Int = 0,
    /** LEARNER, ROAD_SCOUT, NAVIGATOR, ROAD_GUARDIAN or ROAD_LEGEND. */
    val level: String = "LEARNER",
    val levelIndex: Int = 0,
    val nextLevelPoints: Int? = null,
    val stats: ProfileStats = ProfileStats(),
    /** FIRST_REPORT, REPORTER_10, REPORTER_50, HELPFUL_10, CHECKER_25. */
    val badges: List<String> = emptyList(),
) {
    /** Progress towards the next level, 0..1 (1 at the top level). */
    fun progress(levelStarts: List<Int> = LEVEL_STARTS): Float {
        val next = nextLevelPoints ?: return 1f
        val from = levelStarts.getOrElse(levelIndex) { 0 }
        return ((points - from).toFloat() / (next - from)).coerceIn(0f, 1f)
    }

    companion object {
        val LEVEL_STARTS = listOf(0, 100, 500, 2_000, 10_000)
        val ALL_BADGES = listOf("FIRST_REPORT", "REPORTER_10", "REPORTER_50", "HELPFUL_10", "CHECKER_25")
    }
}

@Serializable
data class ShareLink(val id: String, val url: String)

/** One GPS sample in an anonymous traffic segment: seconds since the segment began. */
@Serializable
data class TrafficPoint(val lat: Double, val lon: Double, val t: Int, val speedMps: Double?)

/** A coloured road piece on the traffic layer: FREE, SLOW or JAM. */
@Serializable
data class TrafficLine(val edgeId: Long, val level: String, val speedKph: Int, val expectedKph: Int, val points: List<List<Double>>)
