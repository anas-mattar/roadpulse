package com.roadpulse.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Source of community road reports: [BackendReportRepository] (server, live updates) or
 * [InMemoryReportRepository] (kept on this phone only, used when no server is configured).
 */
interface ReportRepository {
    val reports: StateFlow<List<Report>>
    suspend fun submit(type: ReportType, location: GeoPoint, subtype: String? = null, durationMinutes: Int? = null): Report
    /** "Still there" (true) or "Not there" (false). */
    suspend fun vote(reportId: String, stillThere: Boolean)
    /** Flags a report as abuse for moderators to review. */
    suspend fun flag(reportId: String, reason: FlagReason)
    /** True for reports sent from this phone (you cannot vote on or flag your own). */
    fun isOwn(reportId: String): Boolean
    /** The part of the map on screen; server-backed repositories load and watch it. */
    fun watchArea(visible: MapArea) {}
    /** Stops network activity (called when switching servers or closing the screen). */
    fun close() {}
}

class InMemoryReportRepository : ReportRepository {
    private val state = MutableStateFlow<List<Report>>(emptyList())
    override val reports: StateFlow<List<Report>> = state.asStateFlow()

    override suspend fun submit(type: ReportType, location: GeoPoint, subtype: String?, durationMinutes: Int?): Report {
        val report = Report(UUID.randomUUID().toString(), type, location, System.currentTimeMillis(), subtype = subtype)
        state.update { it + report }
        return report
    }

    // Every report here was made on this phone, so the vote buttons are not shown for them.
    override fun isOwn(reportId: String) = state.value.any { it.id == reportId }

    override suspend fun vote(reportId: String, stillThere: Boolean) {
        state.update { list ->
            if (stillThere) list.map { if (it.id == reportId) it.copy(confirmations = it.confirmations + 1) else it }
            else list.filterNot { it.id == reportId }
        }
    }

    override suspend fun flag(reportId: String, reason: FlagReason) {
        state.update { list -> list.filterNot { it.id == reportId } }
    }
}
