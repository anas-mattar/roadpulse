package com.roadpulse.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Report as sent by the backend (REST and live updates). */
@Serializable
data class ReportDto(
    val id: String,
    val type: ReportType,
    val lat: Double,
    val lon: Double,
    val headingDeg: Int? = null,
    val createdAt: String,
    val expiresAt: String,
    val confirmations: Int = 0,
    val dismissals: Int = 0,
    val subtype: String? = null,
) {
    fun toReport() = Report(
        id = id,
        type = type,
        location = GeoPoint(lat, lon),
        createdAtMillis = IsoTime.parseMillis(createdAt),
        confirmations = confirmations,
        dismissals = dismissals,
        subtype = subtype,
        expiresAtMillis = IsoTime.parseMillis(expiresAt),
    )
}

@Serializable
data class CreateReportBody(
    val type: ReportType,
    val lat: Double,
    val lon: Double,
    val subtype: String? = null,
    val durationMinutes: Int? = null,
)

@Serializable
data class CreateReportResult(val report: ReportDto, val merged: Boolean)

@Serializable
data class Registration(val deviceId: String, val token: String)

@Serializable
data class VoteBody(val stillThere: Boolean)

@Serializable
data class FlagBody(val reason: FlagReason)

/** Reports on the RoadPulse backend, as this anonymous device (used by the iPhone app). */
class ReportsClient(private val baseUrl: String, private val api: DeviceApi) {
    /** Active reports in [area]. Public: no device token needed. */
    suspend fun list(area: MapArea): List<Report> =
        Http.json.decodeFromString(ListSerializer(ReportDto.serializer()), Http.execute("$baseUrl/v1/reports?bbox=${area.bboxParam()}"))
            .map { it.toReport() }

    suspend fun submit(type: ReportType, at: GeoPoint, subtype: String? = null, durationMinutes: Int? = null): Report {
        val body = Http.json.encodeToString(CreateReportBody.serializer(), CreateReportBody(type, at.lat, at.lon, subtype, durationMinutes))
        return Http.json.decodeFromString(CreateReportResult.serializer(), api.call("POST", "/v1/reports", body)).report.toReport()
    }

    suspend fun vote(reportId: String, stillThere: Boolean) {
        api.call("POST", "/v1/reports/$reportId/votes", Http.json.encodeToString(VoteBody.serializer(), VoteBody(stillThere)))
    }

    suspend fun flag(reportId: String, reason: FlagReason) {
        api.call("POST", "/v1/reports/$reportId/flags", Http.json.encodeToString(FlagBody.serializer(), FlagBody(reason)))
    }
}

/** Reads the server's ISO 8601 times ("2026-11-14T13:00:12.772446+00:00") as epoch milliseconds. */
object IsoTime {
    private val PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})$""")

    fun parseMillis(text: String): Long {
        val m = PATTERN.matchEntire(text.trim()) ?: throw IllegalArgumentException("Not an ISO time: $text")
        val (y, mo, d, h, mi, s) = m.destructured
        val fraction = m.groupValues[7]
        val zone = m.groupValues[8]
        val millis = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').take(3).toInt()
        val offsetMinutes = if (zone == "Z") 0 else {
            val sign = if (zone[0] == '-') -1 else 1
            sign * (zone.substring(1, 3).toInt() * 60 + zone.substring(4, 6).toInt())
        }
        val days = daysFromCivil(y.toInt(), mo.toInt(), d.toInt())
        val seconds = days * 86_400L + h.toInt() * 3_600L + mi.toInt() * 60L + s.toInt() - offsetMinutes * 60L
        return seconds * 1_000 + millis
    }

    /** Days since 1970-01-01 (Howard Hinnant's algorithm). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
