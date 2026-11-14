package com.roadpulse.app.nav

import com.roadpulse.app.BuildConfig
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.data.SpeedLimits
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface NavEvent {
    data class Speak(val text: String) : NavEvent
    data object Arrived : NavEvent
}

data class NavState(
    val route: Route,
    val destination: Place,
    val instruction: String?,
    val distanceToTurnMeters: Double?,
    val remainingKm: Double,
    val remainingSeconds: Double,
    val rerouting: Boolean = false,
    /** Position snapped to the route (or the raw fix when off route); null before the first fix. */
    val displayPosition: GeoPoint? = null,
    val poorSignal: Boolean = false,
    /** No usable GPS fix for a while (tunnel, car park, GPS off). */
    val gpsLost: Boolean = false,
    /** The last reroute failed for lack of network; the previous route is kept. */
    val offline: Boolean = false,
    /** This drive was restored after the app was stopped. */
    val resumed: Boolean = false,
    /** Maneuver type of the next turn (drives the turn arrow). */
    val upcomingType: Int? = null,
    /** The turn after that, shown as "Then …" when it comes soon after. */
    val thenType: Int? = null,
    val thenGapMeters: Double? = null,
    /** Current speed from GPS, if the provider reports it. */
    val speedMps: Double? = null,
    /** Posted speed limit on the current road, if OpenStreetMap has one. */
    val speedLimitKph: Int? = null,
)

/**
 * The single active drive. Shared by [NavigationService] (feeds GPS fixes, speaks prompts,
 * keeps running with the screen off) and the UI (renders [state]).
 */
object NavigationSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val router by lazy { RoutingClient(BuildConfig.ROUTING_URL) }

    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    /** Monotonic clock shared by GPS fixes, the simulator and the log (set to elapsedRealtime on device). */
    var clock: () -> Long = { System.nanoTime() / 1_000_000 }
    fun now(): Long = clock()

    private var store: DriveStore? = null
    private var persist = true
    private var tracker: RouteTracker? = null
    private var log: NavLog? = null
    private var rerouteJob: Job? = null
    private var watchdogJob: Job? = null
    private var limitsJob: Job? = null
    private var speedLimits: SpeedLimits = SpeedLimits.NONE
    private var lastRerouteAttemptMillis = Long.MIN_VALUE / 2
    private val signal = SignalMonitor()

    /** Called once from the activity and the service; safe to repeat. */
    fun init(driveStore: DriveStore, monotonicClock: () -> Long) {
        if (store == null) store = driveStore
        clock = monotonicClock
    }

    /**
     * Begins a new drive.
     * @param persistDrive save it so it can resume after the app is stopped (false for simulations).
     */
    fun start(route: Route, destination: Place, persistDrive: Boolean = true) {
        rerouteJob?.cancel()
        persist = persistDrive
        applyRoute(route, destination, resumed = false)
        startWatchdog()
    }

    /** Restores a saved drive if one exists and none is active. Returns true if a drive is now active. */
    fun restoreIfSaved(): Boolean {
        if (_state.value != null) return true
        val saved = store?.load() ?: return false
        persist = true
        applyRoute(saved.route, saved.destination, resumed = true)
        startWatchdog()
        return true
    }

    /** Debug builds: records this drive to [navLog] until it stops. */
    fun attachLog(navLog: NavLog, replay: DriveReplay.Config?) {
        log?.close()
        log = navLog
        val s = _state.value
        navLog.event(
            now(), "drive_start",
            "destination=${s?.destination?.name};route_km=${s?.route?.lengthKm};" +
                "route_min=${s?.route?.timeSeconds?.div(60)?.toInt()};resumed=${s?.resumed};" +
                "source=${replay?.label ?: "device GPS"}",
        )
    }

    /** Speaks (and logs) the first instruction of the current route. */
    fun announceStart() {
        val s = _state.value ?: return
        val n = VoiceSettings.narrator()
        speak(if (s.resumed) n.resuming(s.destination.name) else null)
        speak(s.route.maneuvers.firstOrNull()?.let(n::atTurn))
    }

    fun stop() {
        rerouteJob?.cancel()
        limitsJob?.cancel()
        speedLimits = SpeedLimits.NONE
        watchdogJob?.cancel()
        tracker = null
        store?.clear()
        log?.close()
        log = null
        _state.value = null
    }

    fun onLocation(fix: LocationFix) {
        val current = _state.value ?: return
        val t = tracker ?: return

        val usable = (fix.accuracyM ?: 0.0) <= RouteTracker.POOR_ACCURACY_M
        if (signal.onFix(now(), usable)) {
            log?.event(now(), "gps_restored")
            _state.update { it?.copy(gpsLost = false) }
        }
        // While a reroute is pending, keep tracking on the current route (position, distances);
        // no prompts fire because the driver is off route, and no second reroute starts.
        val progress = t.update(fix)
        log?.fix(fix, progress)
        if (progress.arrived) {
            log?.event(fix.timeMillis, "arrived")
            speak(VoiceSettings.narrator().arrived)
            _events.tryEmit(NavEvent.Arrived)
            stop()
            return
        }
        if (progress.offRoute) {
            if (!current.rerouting) reroute(fix.position)
            _state.update {
                it?.copy(displayPosition = progress.displayPosition, poorSignal = progress.poorSignal, speedMps = fix.speedMps)
            }
            return
        }
        progress.prompt?.let { speak(it, fix.timeMillis) }
        // Back on the route: the kept route is valid again, so "offline" no longer applies.
        val backOnRoute = progress.matched && current.offline
        if (backOnRoute) log?.event(fix.timeMillis, "back_on_route", "offline cleared")
        _state.update {
            it?.copy(
                offline = if (progress.matched) false else it.offline,
                instruction = progress.upcoming?.let { m -> PromptText.tidy(VoiceSettings.narrator().banner(m)) },
                upcomingType = progress.upcoming?.type,
                thenType = progress.following?.type,
                thenGapMeters = progress.followingGapMeters,
                speedMps = fix.speedMps,
                speedLimitKph = if (progress.matched) speedLimits.at(progress.segmentIndex) else it.speedLimitKph,
                distanceToTurnMeters = progress.distanceToTurnMeters,
                remainingKm = progress.remainingMeters / 1000,
                remainingSeconds = progress.remainingSeconds,
                displayPosition = progress.displayPosition,
                poorSignal = progress.poorSignal,
            )
        }
    }

    private fun applyRoute(route: Route, destination: Place, resumed: Boolean) {
        tracker = RouteTracker(route, resumed, VoiceSettings::narrator)
        fetchSpeedLimits(route)
        signal.reset(now())
        if (persist) store?.save(route, destination)
        val previous = _state.value
        _state.value = NavState(
            route = route,
            destination = destination,
            instruction = route.maneuvers.firstOrNull()?.let { m -> PromptText.tidy(VoiceSettings.narrator().banner(m)) },
            distanceToTurnMeters = null,
            remainingKm = route.lengthKm,
            remainingSeconds = route.timeSeconds,
            displayPosition = previous?.displayPosition,
            resumed = resumed,
            upcomingType = route.maneuvers.firstOrNull()?.type,
            speedMps = previous?.speedMps,
        )
    }

    /** Loads speed limits for [route] in the background; none are shown until they arrive. */
    private fun fetchSpeedLimits(route: Route) {
        limitsJob?.cancel()
        speedLimits = SpeedLimits.NONE
        limitsJob = scope.launch {
            try {
                val limits = router.speedLimits(route)
                if (_state.value?.route === route) speedLimits = limits
                log?.event(now(), "speed_limits", if (limits.isEmpty) "none" else "loaded")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.event(now(), "speed_limits", "failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun speak(text: String?, timeMillis: Long = now()) {
        if (text.isNullOrBlank()) return
        val tidy = PromptText.tidy(text)
        log?.event(timeMillis, "prompt", tidy)
        _events.tryEmit(NavEvent.Speak(tidy))
    }

    /** Checks once a second whether GPS has gone quiet. */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (_state.value == null) break
                if (signal.check(now())) {
                    log?.event(now(), "gps_lost")
                    _state.update { it?.copy(gpsLost = true) }
                    speak(VoiceSettings.narrator().gpsLost)
                }
            }
        }
    }

    private fun reroute(from: GeoPoint) {
        val current = _state.value ?: return
        val t = now()
        if (t - lastRerouteAttemptMillis < REROUTE_MIN_INTERVAL_MS) return
        lastRerouteAttemptMillis = t
        _state.value = current.copy(rerouting = true)
        log?.event(t, "reroute_start", "from=${from.lat},${from.lon}")
        if (!current.offline) speak(VoiceSettings.narrator().rerouting)
        rerouteJob = scope.launch {
            try {
                val route = router.route(from, current.destination.location)
                if (_state.value == null) return@launch
                log?.event(now(), "reroute_done", "route_km=${route.lengthKm}")
                applyRoute(route, current.destination, resumed = false)
                speak(route.maneuvers.firstOrNull()?.let(VoiceSettings.narrator()::atTurn))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val noNetwork = e is IOException
                log?.event(now(), "reroute_failed", "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
                val wasOffline = _state.value?.offline == true
                _state.update { it?.copy(rerouting = false, offline = noNetwork) }
                if (noNetwork && !wasOffline) speak(VoiceSettings.narrator().noInternet)
                // Retried on a later off-route fix, at most every REROUTE_MIN_INTERVAL_MS.
            }
        }
    }

    private const val REROUTE_MIN_INTERVAL_MS = 10_000L
}
