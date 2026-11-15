package com.roadpulse.app.nav

import com.roadpulse.app.data.DeviceApi
import com.roadpulse.app.data.DeviceTokenStore
import com.roadpulse.app.data.ShareClient
import com.roadpulse.app.data.ShareLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.roadpulse.app.util.wallClockMillis

/**
 * "Share my drive": one live link for the current drive. While it is active the app sends the
 * car's position and arrival time every [UPDATE_MS]; on arrival the link says so (and ends 30
 * minutes later on the server); stopping the drive or tapping Stop sharing ends it at once.
 * Shared by Android and iPhone.
 */
object ShareSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _link = MutableStateFlow<ShareLink?>(null)
    /** The active link, or null when the drive is not shared. */
    val link: StateFlow<ShareLink?> = _link.asStateFlow()
    private val _viewers = MutableStateFlow(0)
    /** People with the share page open right now (from the last update). */
    val viewers: StateFlow<Int> = _viewers.asStateFlow()
    private var client: ShareClient? = null
    private var job: Job? = null

    /** Starts sharing the current drive; returns the link to send, or throws if the server refuses. */
    suspend fun start(backendUrl: String, tokens: DeviceTokenStore, name: String?): ShareLink {
        _link.value?.let { return it }
        val state = NavigationSession.state.value ?: error("No drive in progress")
        require(backendUrl.isNotEmpty()) { "No server" }
        val c = ShareClient(DeviceApi(backendUrl, tokens))
        val position = state.displayPosition ?: state.route.shape.first()
        val link = c.start(state.destination, position, eta(state.remainingSeconds), name)
        client = c
        _link.value = link
        job = scope.launch { followDrive(c, link) }
        return link
    }

    private suspend fun followDrive(c: ShareClient, link: ShareLink) {
        var lastSent = wallClockMillis()
        while (true) {
            delay(1_000)
            val s = NavigationSession.state.value
            if (s == null) {
                // The drive ended: arrived (the session announced it) or stopped by the driver.
                if (arrived) runCatching { c.update(link.id, lastPosition ?: return@runCatching, wallClockMillis(), "ARRIVED") }
                else c.stop(link.id)
                finish()
                return
            }
            lastPosition = s.displayPosition ?: lastPosition
            arrived = arrived || s.remainingKm < 0.05
            if (wallClockMillis() - lastSent >= UPDATE_MS) {
                lastSent = wallClockMillis()
                s.displayPosition?.let { p -> runCatching { c.update(link.id, p, eta(s.remainingSeconds), "DRIVING") }.onSuccess { _viewers.value = it } }
            }
        }
    }

    private var lastPosition: com.roadpulse.app.data.GeoPoint? = null
    private var arrived = false

    /** Called when the drive reaches its destination (before the session clears its state). */
    fun onArrived() { arrived = true }

    /** Stops sharing now (the link stops working). */
    fun stop() {
        val l = _link.value ?: return
        val c = client
        job?.cancel()
        scope.launch { c?.stop(l.id) }
        finish()
    }

    private fun finish() {
        _link.value = null
        _viewers.value = 0
        client = null
        job = null
        lastPosition = null
        arrived = false
    }

    private fun eta(remainingSeconds: Double) = wallClockMillis() + (remainingSeconds * 1000).toLong()

    private const val UPDATE_MS = 20_000L
}
