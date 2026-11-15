package com.roadpulse.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The reports currently known to the app (from the server or this phone), shared with the active
 * drive so it can warn about reports on the route ahead. Updated by the map screen's reports source.
 */
object ReportFeed {
    val reports = MutableStateFlow<List<Report>>(emptyList())
}
