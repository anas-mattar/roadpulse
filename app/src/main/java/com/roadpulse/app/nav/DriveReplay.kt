package com.roadpulse.app.nav

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Debug-only simulated drive. While [active], [NavigationService] and the map use [fixes]
 * instead of the device's real location. Follows the active route, including new routes after a
 * reroute, until navigation stops or arrives.
 */
object DriveReplay {

    data class Config(
        val label: String,
        val speedKmh: Double = 50.0,
        val noiseM: Double = 8.0,
        /** If set, leaves the route sideways at this fraction of the route to test rerouting. */
        val detourAtFraction: Double? = null,
    )

    val presets = listOf(
        Config("Drive the route at 50 km/h"),
        Config("Drive the route at 90 km/h", speedKmh = 90.0),
        Config("Take a wrong turn (tests rerouting)", detourAtFraction = 0.3),
        Config("Bad GPS: 25 m noise", noiseM = 25.0),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _fixes = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
    val fixes: SharedFlow<LocationFix> = _fixes.asSharedFlow()

    var currentConfig: Config? = null
        private set

    fun start(config: Config) {
        stop()
        currentConfig = config
        _active.value = true
        job = scope.launch { run(config) }.also { j ->
            j.invokeOnCompletion { if (job === j || job == null) _active.value = false }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _active.value = false
    }

    private suspend fun run(c: Config) {
        delay(START_DELAY_MS) // let the navigation service subscribe first
        val speed = c.speedKmh / 3.6
        var route = NavigationSession.state.value?.route ?: return
        var sim = DriveSimulator(route.shape, speed, c.noiseM)
        var detourPending = c.detourAtFraction != null
        var endFixes = 0

        while (true) {
            val nav = NavigationSession.state.value ?: return // stopped or arrived
            // A real car keeps moving while a reroute is pending, so keep emitting fixes.
            if (nav.route !== route && !nav.rerouting) { // rerouted: continue on the new route from its start
                route = nav.route
                sim = DriveSimulator(route.shape, speed, c.noiseM)
            }

            if (detourPending && sim.alongM >= c.detourAtFraction!! * sim.totalMeters) {
                detourPending = false
                val start = sim.position()
                val sideways = (sim.bearing() + 90) % 360
                for (k in 1..DETOUR_FIXES) {
                    val p = DriveSimulator.offset(start, sideways, k * speed)
                    _fixes.emit(sim.noisyFix(p, sideways, speed, NavigationSession.now()))
                    delay(TICK_MS)
                    if (NavigationSession.state.value?.let { it.route !== route } != false) break
                }
                continue
            }

            if (sim.finished && ++endFixes > MAX_END_FIXES) return
            _fixes.emit(sim.next(NavigationSession.now()))
            delay(TICK_MS)
        }
    }

    private const val TICK_MS = 1000L
    private const val START_DELAY_MS = 1500L
    private const val DETOUR_FIXES = 30
    private const val MAX_END_FIXES = 10
}
