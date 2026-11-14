package com.roadpulse.app.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Screen offsets (in icon units, roughly pixels at size 1) that fan out reports sitting on almost
 * the same spot, so none hides another. Reports further apart than [nearMeters] keep offset 0,0.
 */
object ReportLayout {
    /** Icons are 34 dp wide; neighbours on the circle are kept at least [spacing] apart. */
    fun offsets(reports: List<Report>, nearMeters: Double = 40.0, minRadius: Float = 18f, spacing: Float = 36f): Map<String, Pair<Float, Float>> {
        val result = HashMap<String, Pair<Float, Float>>()
        val left = reports.sortedBy { it.createdAtMillis }.toMutableList()
        while (left.isNotEmpty()) {
            val first = left.removeAt(0)
            val group = mutableListOf(first)
            val it = left.iterator()
            while (it.hasNext()) {
                val r = it.next()
                if (Geo.distanceMeters(first.location, r.location) <= nearMeters) {
                    group += r
                    it.remove()
                }
            }
            if (group.size == 1) {
                result[first.id] = 0f to 0f
            } else {
                // Distance between neighbours on a circle is 2·r·sin(π/n); grow r so icons do not touch.
                val radius = maxOf(minRadius, (spacing / (2 * sin(PI / group.size))).toFloat())
                group.forEachIndexed { i, r ->
                    val angle = -PI / 2 + 2 * PI * i / group.size // first one at the top, clockwise
                    result[r.id] = (radius * cos(angle)).toFloat() to (radius * sin(angle)).toFloat()
                }
            }
        }
        return result
    }
}

/** Short, friendly age such as "just now", "12 min ago", "2 h ago". */
fun reportAge(createdAtMillis: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - createdAtMillis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}
