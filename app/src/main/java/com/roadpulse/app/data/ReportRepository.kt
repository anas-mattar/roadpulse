package com.roadpulse.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Source of community road reports.
 * Phase 2 replaces [InMemoryReportRepository] with a backend-synced implementation
 * (REST for submit/confirm, WebSocket for live updates in the visible map area).
 */
interface ReportRepository {
    val reports: StateFlow<List<Report>>
    suspend fun submit(type: ReportType, location: GeoPoint): Report
    suspend fun confirm(reportId: String)
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
