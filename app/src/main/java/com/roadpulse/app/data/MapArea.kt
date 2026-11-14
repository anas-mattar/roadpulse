package com.roadpulse.app.data

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** A map rectangle in degrees (WGS84). */
data class MapArea(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    val width get() = maxLon - minLon
    val height get() = maxLat - minLat

    fun contains(other: MapArea) =
        other.minLon >= minLon && other.maxLon <= maxLon && other.minLat >= minLat && other.maxLat <= maxLat

    fun contains(p: GeoPoint) = p.lon in minLon..maxLon && p.lat in minLat..maxLat

    /** Grows each side by [fraction] of the size, so small pans do not need a new subscription. */
    fun expanded(fraction: Double) = MapArea(
        minLon - width * fraction, minLat - height * fraction,
        maxLon + width * fraction, maxLat + height * fraction,
    ).withinWorld()

    /** Same centre, each side at most [maxDegrees] (the server rejects larger areas). */
    fun clampedTo(maxDegrees: Double): MapArea {
        val cLon = (minLon + maxLon) / 2
        val cLat = (minLat + maxLat) / 2
        val halfW = min(width, maxDegrees) / 2
        val halfH = min(height, maxDegrees) / 2
        return MapArea(cLon - halfW, cLat - halfH, cLon + halfW, cLat + halfH).withinWorld()
    }

    private fun withinWorld() = MapArea(max(minLon, -180.0), max(minLat, -90.0), min(maxLon, 180.0), min(maxLat, 90.0))

    /** "minLon,minLat,maxLon,maxLat" for GET /v1/reports. */
    fun bboxParam(): String = String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", minLon, minLat, maxLon, maxLat)

    companion object {
        /** Server limit is 0.5° per side; stay a little under it. */
        const val MAX_DEGREES = 0.48

        /** The area to watch for a visible map region: padded, then clamped to the server limit. */
        fun subscriptionFor(visible: MapArea): MapArea = visible.expanded(0.5).clampedTo(MAX_DEGREES)

        /** A new subscription is needed when the visible map (clamped) leaves the watched area. */
        fun needsNewSubscription(current: MapArea?, visible: MapArea): Boolean =
            current == null || !current.contains(visible.clampedTo(MAX_DEGREES))
    }
}
