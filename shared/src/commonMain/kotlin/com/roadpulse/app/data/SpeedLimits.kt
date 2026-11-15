package com.roadpulse.app.data

/** A stretch of the route (shape point indices [begin, end)) with a posted speed limit. */
data class SpeedLimitSpan(val begin: Int, val end: Int, val kph: Int)

/** Posted speed limits along one route, from OpenStreetMap via the routing server. */
class SpeedLimits(spans: List<SpeedLimitSpan>) {
    private val spans = spans.sortedBy { it.begin }

    /** Limit on the route segment that starts at shape point [segmentIndex], or null if unknown. */
    fun at(segmentIndex: Int): Int? {
        var lo = 0
        var hi = spans.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = spans[mid]
            when {
                segmentIndex < s.begin -> hi = mid - 1
                segmentIndex >= s.end -> lo = mid + 1
                else -> return s.kph
            }
        }
        return null
    }

    val isEmpty: Boolean get() = spans.isEmpty()

    companion object {
        val NONE = SpeedLimits(emptyList())
    }
}
