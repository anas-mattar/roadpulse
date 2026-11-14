package com.roadpulse.app.nav

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Sun position, to switch the map to its night style after sunset. Pure Kotlin: unit tested. */
object SunTimes {
    /** Sun elevation above the horizon in degrees (low-precision formula, ±0.5° — plenty here). */
    fun elevationDeg(lat: Double, lon: Double, epochMillis: Long): Double {
        val d = epochMillis / 86_400_000.0 + 2_440_587.5 - 2_451_545.0 // days since J2000
        val g = Math.toRadians(357.529 + 0.98560028 * d)
        val q = 280.459 + 0.98564736 * d
        val l = Math.toRadians(q + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val ra = Math.toDegrees(atan2(cos(e) * sin(l), cos(l)))
        val dec = asin(sin(e) * sin(l))
        val gmstDeg = (18.697374558 + 24.06570982441908 * d) * 15.0
        val ha = Math.toRadians(gmstDeg + lon - ra)
        val latR = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(latR) * sin(dec) + cos(latR) * cos(dec) * cos(ha)))
    }

    /** Night = sun below the horizon (standard sunset/sunrise refraction allowance). */
    fun isNight(lat: Double, lon: Double, epochMillis: Long): Boolean =
        elevationDeg(lat, lon, epochMillis) < -0.833
}
