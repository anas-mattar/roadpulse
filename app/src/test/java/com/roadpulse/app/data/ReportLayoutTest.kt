package com.roadpulse.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.hypot

class ReportLayoutTest {
    private fun report(id: String, lat: Double, lon: Double, t: Long) = Report(id, ReportType.HAZARD, GeoPoint(lat, lon), t)

    @Test
    fun loneReportsStayInPlace() {
        val offsets = ReportLayout.offsets(listOf(report("a", 3.10, 101.60, 1), report("b", 3.12, 101.62, 2)))
        assertEquals(0f to 0f, offsets["a"])
        assertEquals(0f to 0f, offsets["b"])
    }

    @Test
    fun reportsOnTheSameSpotFanOutOldestOnTop() {
        // Hazard and police 10 m apart (the Brickfields case), plus a road closure 30 m away.
        val offsets = ReportLayout.offsets(
            listOf(report("police", 3.13860, 101.68900, 2), report("hazard", 3.13851, 101.68873, 1), report("closed", 3.13878, 101.68880, 3))
        )
        val hazard = offsets["hazard"]!!
        assertEquals(0f, hazard.first, 1e-4f)
        val radius = 36.0 / (2 * kotlin.math.sin(Math.PI / 3)) // about 20.8
        assertEquals(-radius.toFloat(), hazard.second, 1e-3f) // oldest at the top
        listOf("police", "closed").forEach { id ->
            val (dx, dy) = offsets[id]!!
            assertEquals(radius, hypot(dx.toDouble(), dy.toDouble()), 1e-3)
        }
        assertNotEquals(offsets["police"], offsets["closed"])
    }

    @Test
    fun biggerGroupsGetABiggerCircleSoIconsNeverTouch() {
        // Six reports on one spot, like the Brickfields test group.
        val group = (1..6).map { report("r$it", 3.1386 + it * 0.00002, 101.689, it.toLong()) }
        val points = ReportLayout.offsets(group).values.toList()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val d = hypot((points[i].first - points[j].first).toDouble(), (points[i].second - points[j].second).toDouble())
            assert(d >= 36.0 - 1e-3) { "icons $i and $j only $d apart" }
        }
    }

    @Test
    fun ageText() {
        val t = 1_000_000_000L
        assertEquals("just now", reportAge(t, t + 30_000))
        assertEquals("12 min ago", reportAge(t, t + 12 * 60_000))
        assertEquals("2 h ago", reportAge(t, t + 125 * 60_000))
        assertEquals("just now", reportAge(t, t - 5_000)) // clocks slightly apart
    }
}
