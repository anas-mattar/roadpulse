package com.roadpulse.app.nav

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.util.Locale

/**
 * Debug drive log: one CSV row per GPS fix and per navigation event (prompts, reroutes,
 * arrival), plus a summary row at the end. Written only in debug builds, to app-private storage,
 * and never uploaded. Contains precise locations: treat log files as personal data.
 */
class NavLog(val file: File, private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) : Closeable {
    private val out = PrintWriter(file.bufferedWriter())
    private var closed = false

    var fixes = 0; private set
    var reroutes = 0; private set
    var prompts = 0; private set
    var poorFixes = 0; private set
    var maxMatchedOffsetM = 0.0; private set

    init {
        out.println(
            "time_ms,event,lat,lon,accuracy_m,bearing_deg,speed_mps," +
                "matched_lat,matched_lon,offset_m,matched,off_route,to_turn_m,remaining_m,detail"
        )
    }

    fun fix(fix: LocationFix, p: Progress) {
        if (closed) return
        fixes++
        if (p.poorSignal) poorFixes++
        if (p.matched) maxMatchedOffsetM = maxOf(maxMatchedOffsetM, p.offRouteMeters)
        row(
            fix.timeMillis, "fix", fix.position.lat, fix.position.lon, fix.accuracyM, fix.bearingDeg, fix.speedMps,
            p.displayPosition.lat, p.displayPosition.lon, p.offRouteMeters, p.matched, p.offRoute,
            p.distanceToTurnMeters, p.remainingMeters, if (p.poorSignal) "poor_signal" else "",
        )
    }

    fun event(timeMillis: Long, name: String, detail: String = "") {
        if (closed) return
        when (name) {
            "prompt" -> prompts++
            "reroute_start" -> reroutes++
        }
        row(timeMillis, name, detail = detail)
    }

    fun summary(): String = "fixes=$fixes;reroutes=$reroutes;prompts=$prompts;poor_fixes=$poorFixes;" +
        "max_matched_offset_m=${"%.1f".format(Locale.US, maxMatchedOffsetM)}"

    override fun close() {
        if (closed) return
        event(clock(), "summary", summary())
        closed = true
        out.close()
    }

    private fun row(
        time: Long, event: String,
        lat: Double? = null, lon: Double? = null, acc: Double? = null, bearing: Double? = null, speed: Double? = null,
        mLat: Double? = null, mLon: Double? = null, offset: Double? = null, matched: Boolean? = null,
        offRoute: Boolean? = null, toTurn: Double? = null, remaining: Double? = null, detail: String = "",
    ) {
        fun d(v: Double?, digits: Int) = v?.let { "%.${digits}f".format(Locale.US, it) } ?: ""
        out.println(
            listOf(
                time.toString(), event, d(lat, 6), d(lon, 6), d(acc, 1), d(bearing, 0), d(speed, 1),
                d(mLat, 6), d(mLon, 6), d(offset, 1), matched?.toString() ?: "", offRoute?.toString() ?: "",
                d(toTurn, 0), d(remaining, 0), "\"" + detail.replace("\"", "\"\"") + "\"",
            ).joinToString(",")
        )
        out.flush()
    }
}
