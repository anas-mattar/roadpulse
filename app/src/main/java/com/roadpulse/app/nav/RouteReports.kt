package com.roadpulse.app.nav

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Report
import kotlin.math.cos
import kotlin.math.sqrt

/** A report on the route ahead and how far along the route it is from the car. */
data class ReportAhead(val report: Report, val metersAhead: Double)

/**
 * Finds community reports that lie on the active route, ahead of the car. A report counts when it
 * is within [corridorM] of the route line; its position along the route is cached per report.
 * Distances use the same measure as [RouteMatcher], so "along" values are comparable.
 * Pure Kotlin: unit tested.
 */
class RouteReports(private val shape: List<GeoPoint>, private val corridorM: Double = 30.0) {
    private val cumulative = DoubleArray(shape.size).also { c ->
        for (i in 1 until shape.size) c[i] = c[i - 1] + Geo.distanceMeters(shape[i - 1], shape[i])
    }
    val totalMeters: Double = cumulative.lastOrNull() ?: 0.0
    private val alongCache = HashMap<String, Double?>()

    /** Metres from the route start to shape point [index]. */
    fun alongAt(index: Int): Double = cumulative[index.coerceIn(0, cumulative.lastIndex)]

    /** Metres from the route start to the report, or null if it is not on the route. */
    fun alongOf(report: Report): Double? = alongCache.getOrPut(report.id) { project(report.location) }

    /** Metres from the route start to a point identified by [id], or null if it is not on the route. */
    fun alongOfPoint(id: String, point: GeoPoint): Double? = alongCache.getOrPut(id) { project(point) }

    /** Reports ahead of [carAlongM], nearest first, up to [maxAheadM] away. */
    fun ahead(reports: List<Report>, carAlongM: Double, maxAheadM: Double): List<ReportAhead> =
        reports.mapNotNull { r ->
            val along = alongOf(r) ?: return@mapNotNull null
            val ahead = along - carAlongM
            if (ahead > 0 && ahead <= maxAheadM) ReportAhead(r, ahead) else null
        }.sortedBy { it.metersAhead }

    private fun project(p: GeoPoint): Double? {
        var best: Double? = null
        var bestOffset = Double.MAX_VALUE
        val k = cos(Math.toRadians(p.lat))
        for (i in 0 until shape.size - 1) {
            val a = shape[i]
            val b = shape[i + 1]
            val ax = (a.lon - p.lon) * k * M_PER_DEG
            val ay = (a.lat - p.lat) * M_PER_DEG
            val dx = (b.lon - a.lon) * k * M_PER_DEG
            val dy = (b.lat - a.lat) * M_PER_DEG
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val offset = sqrt(px * px + py * py)
            if (offset <= corridorM && offset < bestOffset) {
                bestOffset = offset
                best = cumulative[i] + t * (cumulative[i + 1] - cumulative[i])
            }
        }
        return best
    }

    companion object {
        private const val M_PER_DEG = 111_195.0
    }
}

/**
 * Decides when to warn about reports ahead: once per report, when it first comes within
 * [announceM] (rounded to 100 m in the prompt). Pure Kotlin: unit tested.
 */
class ReportAnnouncer(private val announceM: Double = 800.0) {
    private val announced = HashSet<String>()

    /** Returns the report to announce now, if any (the nearest one not yet announced). */
    fun next(ahead: List<ReportAhead>): ReportAhead? {
        val due = ahead.firstOrNull { it.metersAhead <= announceM && it.report.id !in announced } ?: return null
        announced += due.report.id
        return due
    }

    companion object {
        /** Spoken distance: rounded to 100 m, at least 100 m. */
        fun spokenMeters(meters: Double): Int = (Math.round(meters / 100.0) * 100).toInt().coerceAtLeast(100)
    }
}

/**
 * Notices when the car drives past a report on the route: the report was within [nearM] ahead and
 * is then no longer ahead. Each report is reported once. Pure Kotlin: unit tested.
 */
class PassedReports(private val nearM: Double = 80.0) {
    private val close = HashSet<String>()
    private val done = HashSet<String>()

    /** Feed the reports ahead after each position fix; returns the ids just passed. */
    fun update(ahead: List<ReportAhead>): List<String> {
        val aheadIds = ahead.map { it.report.id }.toSet()
        val passed = close.filter { it !in aheadIds && it !in done }
        close.retainAll(aheadIds)
        close += ahead.filter { it.metersAhead <= nearM }.map { it.report.id }
        done += passed
        return passed
    }

    /** A new route: reports that are no longer on it were not passed. */
    fun newRoute() = close.clear()

    fun reset() {
        close.clear()
        done.clear()
    }
}

/** A speed camera on the route ahead. */
data class CameraAhead(val id: String, val metersAhead: Double, val maxSpeedKph: Int?)

/** A school on the route ahead; [alongM] is its position along the route. */
data class SchoolAhead(val id: String, val metersAhead: Double, val alongM: Double = 0.0)

/**
 * Decides when to say "School zone ahead": once per stretch of schools (several schools close
 * together get one warning), from [warnM] away, and not on fast roads (limit 80 km/h or more:
 * expressways pass schools without a school zone). Pure Kotlin: unit tested.
 */
class SchoolZoneAnnouncer(private val warnM: Double = 300.0, private val groupM: Double = 600.0) {
    private var lastWarnedAlongM: Double? = null

    fun shouldWarn(school: SchoolAhead, carAlongM: Double, speedLimitKph: Int?): Boolean {
        if (school.metersAhead > warnM) return false
        if (speedLimitKph != null && speedLimitKph >= 80) return false
        val last = lastWarnedAlongM
        if (last != null && school.alongM - last < groupM && school.alongM >= last) return false
        lastWarnedAlongM = school.alongM
        return true
    }

    fun reset() { lastWarnedAlongM = null }
}

