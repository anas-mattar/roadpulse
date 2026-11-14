package com.roadpulse.app.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/** Whether a place is open at a given moment, from its OpenStreetMap opening_hours. */
sealed interface OpenStatus {
    data object AlwaysOpen : OpenStatus
    /** Open now; [closesAt] is when the current opening period ends. */
    data class Open(val closesAt: LocalTime) : OpenStatus
    /** Closed now; [opensAt] is the next opening within 24 hours, or null if later than that. */
    data class Closed(val opensAt: LocalTime?) : OpenStatus
}

/**
 * Reads the common forms of OpenStreetMap's opening_hours, as used by Malaysian fuel stations,
 * shops and car parks: "24/7", "Mo-Fr 08:00-18:00; Sa 09:00-13:00", "Mo-Su 07:00-23:00",
 * "08:00-22:00", "Mo,We 10:00-14:00,16:00-20:00", "Su off", overnight "18:00-02:00".
 * Anything else (months, weeks, sunrise, "PH" only rules, open ends) gives null, so the app shows
 * nothing rather than a wrong answer. Later rules replace earlier ones for their days, as in OSM.
 * Pure Kotlin: unit tested.
 */
object OpeningHours {
    private val DAYS = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val TIME = Regex("""^(\d{1,2}):(\d{2})-(\d{1,2}):(\d{2})$""")

    /** Opening periods for each weekday (0 = Monday) in minutes from that day's midnight; an end
     * after 1440 runs into the next day. Null when the text is not understood. */
    fun parse(text: String?): List<List<IntRange>>? {
        val src = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (src == "24/7") return List(7) { listOf(0..1440) }
        val week = MutableList(7) { emptyList<IntRange>() }
        var any = false
        for (raw in src.replace("||", ";").split(';')) {
            val rule = raw.trim()
            if (rule.isEmpty()) continue
            val parts = rule.split(Regex("\\s+"), limit = 2)
            val (dayPart, timePart) = if (parts[0].first().isLetter() && parts[0] !in setOf("off", "closed")) {
                parts[0] to parts.getOrNull(1)?.trim()
            } else {
                null to rule
            }
            val days = if (dayPart == null) (0..6).toList() else parseDays(dayPart) ?: return null
            if (days.isEmpty()) continue // e.g. a public-holiday-only rule: ignored
            val periods = when {
                timePart == null -> return null
                timePart == "off" || timePart == "closed" -> emptyList()
                timePart == "24/7" || timePart == "00:00-24:00" -> listOf(0..1440)
                else -> timePart.split(',').map { parseTime(it.trim()) ?: return null }
            }
            days.forEach { week[it] = periods }
            any = true
        }
        return if (any) week else null
    }

    /** "Mo-Fr", "Sa,Su", "Mo-We,Fr", "Mo-Fr,PH". Public/school holidays are skipped. */
    private fun parseDays(s: String): List<Int>? {
        val out = mutableListOf<Int>()
        for (token in s.split(',')) {
            when {
                token == "PH" || token == "SH" -> Unit
                '-' in token -> {
                    val (a, b) = token.split('-', limit = 2)
                    val from = DAYS.indexOf(a)
                    val to = DAYS.indexOf(b)
                    if (from < 0 || to < 0) return null
                    var d = from
                    while (true) {
                        out += d
                        if (d == to) break
                        d = (d + 1) % 7
                    }
                }
                else -> out += DAYS.indexOf(token).takeIf { it >= 0 } ?: return null
            }
        }
        return out
    }

    private fun parseTime(s: String): IntRange? {
        val m = TIME.matchEntire(s) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val start = h1.toInt() * 60 + m1.toInt()
        var end = h2.toInt() * 60 + m2.toInt()
        if (start > 1440 || end > 1440 || m1.toInt() > 59 || m2.toInt() > 59) return null
        if (end <= start) end += 1440 // overnight, e.g. 18:00-02:00
        return start..end
    }

    fun status(text: String?, now: LocalDateTime): OpenStatus? {
        val week = parse(text) ?: return null
        if (week.all { day -> day.any { it.first == 0 && it.last >= 1440 } }) return OpenStatus.AlwaysOpen
        val today = now.dayOfWeek.index()
        val minute = now.hour * 60 + now.minute
        // Open now: a period from today, or one from yesterday that runs past midnight.
        week[today].firstOrNull { minute >= it.first && minute < it.last }?.let { return OpenStatus.Open(time(it.last)) }
        week[(today + 6) % 7].firstOrNull { it.last > 1440 && minute < it.last - 1440 }?.let { return OpenStatus.Open(time(it.last)) }
        // Closed: the next opening within 24 hours.
        val next = (0..1).flatMap { offset ->
            week[(today + offset) % 7].map { it.first + offset * 1440 }
        }.filter { it > minute && it - minute <= 1440 }.minOrNull()
        return OpenStatus.Closed(next?.let(::time))
    }

    private fun DayOfWeek.index() = value - 1
    private fun time(minutes: Int): LocalTime = LocalTime.of((minutes % 1440) / 60, minutes % 60)
}
