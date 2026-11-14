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
    suspend fun submit(type: ReportType, location: GeoPoint): Report
    suspend fun confirm(reportId: String)
    /** The part of the map on screen; server-backed repositories load and watch it. */
    fun watchArea(visible: MapArea) {}
    /** Stops network activity (called when switching servers or closing the screen). */
    fun close() {}
}

class InMemoryReportRepository : ReportRepository {
    private val state = MutableStateFlow<List<Report>>(emptyList())
    override val reports: StateFlow<List<Report>> = state.asStateFlow()

    override suspend fun submit(type: ReportType, location: GeoPoint): Report {
        val report = Report(UUID.randomUUID().toString(), type, location, System.currentTimeMillis())
        state.update { it + report }
        return report
    }

    override suspend fun confirm(reportId: String) {
        state.update { list ->
            list.map { if (it.id == reportId) it.copy(confirmations = it.confirmations + 1) else it }
        }
    }
}
