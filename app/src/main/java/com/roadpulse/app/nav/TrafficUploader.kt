package com.roadpulse.app.nav

import android.content.Context
import android.content.SharedPreferences
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.TrafficPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sqrt

/** The driver's opt-in to help with live traffic, and the upload of segments (fire and forget). */
object TrafficUploader {
    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(false)
    /** Off unless the driver turns it on in Settings. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val segmenter = TrafficSegmenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _enabled.value = p.getBoolean(KEY, false)
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        // Record what was agreed to and when (kept on the phone; shown again if the wording changes).
        prefs?.edit()?.putBoolean(KEY, on)?.apply {
            if (on) putLong("traffic_consent_at", System.currentTimeMillis()).putString("traffic_consent_version", CONSENT_VERSION)
        }?.apply()
        if (!on) segmenter.reset()
    }

    /** Called for each GPS fix while navigating (never for simulated drives). */
    fun onFix(position: GeoPoint, speedMps: Double?, timeMillis: Long, remainingM: Double) {
        if (!_enabled.value) return
        val url = com.roadpulse.app.data.ServerSettings.backendUrl()
        if (url.isEmpty()) return
        val segment = segmenter.add(position, speedMps, timeMillis, remainingM) ?: return
        // Sent once; on failure it is simply dropped (no queue of old positions on the phone).
        scope.launch { runCatching { com.roadpulse.app.data.TrafficClient(url).upload(segment) } }
    }

    fun driveEnded() = segmenter.reset()

    private const val KEY = "traffic_opt_in"
    const val CONSENT_VERSION = "2026-11-15"
}
