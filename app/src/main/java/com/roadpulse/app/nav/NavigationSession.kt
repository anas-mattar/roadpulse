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
    /** The car just drove past this report: a good moment to ask "still there?". */
    data class PassedReport(val report: com.roadpulse.app.data.Report) : NavEvent
    /** Speed went over the limit plus the driver's margin (the service plays a short sound). */
    data object SpeedingAlert : NavEvent
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
    /** Nearest community report on the route ahead (within 1 km), shown as a hint. */
    val reportAhead: ReportAhead? = null,
    /** Nearest fixed speed camera on the route ahead (within 1 km). */
    val cameraAhead: CameraAhead? = null,
    /** Lanes before the next maneuver, left to right (empty when the map has none). */
    val lanes: List<com.roadpulse.app.data.Lane> = emptyList(),
    /** Nearest school on the route ahead (within 600 m). */
    val schoolAhead: SchoolAhead? = null,
)

/**
 * The single active drive. Shared by [NavigationService] (feeds GPS fixes, speaks prompts,
 * keeps running with the screen off) and the UI (renders [state]).
 */
object NavigationSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    // Read on each use, so the debug server switch also applies to reroutes.
    private val router get() = RoutingClient(com.roadpulse.app.data.ServerSettings.routingUrl())

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
    private var routeReports: RouteReports? = null
    private var announcer = ReportAnnouncer()
    /** Car position along the current route (metres), from the last matched fix. */
    private var carAlongM = 0.0
    /** Notices when the car has driven past a report (for "still there?"). */
    private val passedReports = PassedReports(PASS_NEAR_M)
    private var wasSpeeding = false
    /** Fixed speed cameras on the current route (from the backend's map places). */
    private var cameras: List<com.roadpulse.app.data.MapPlace> = emptyList()
    private var camerasJob: Job? = null
    private val announcedCameras = HashSet<String>()
    /** Schools along the current route; they are set back from the road, hence a wider corridor. */
    private var schools: List<com.roadpulse.app.data.MapPlace> = emptyList()
    private var schoolRoute: RouteReports? = null
    private var schoolsJob: Job? = null
    private val schoolAlerts = SchoolZoneAnnouncer()
    private var lastSpeedingAlert = Long.MIN_VALUE / 2

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

    /** Debug field tests: records a passenger's issue report at the current position. */
    fun flag(category: String) {
        val p = _state.value?.displayPosition
        log?.event(now(), "flag", "$category;at=${p?.lat},${p?.lon}")
    }

    /** Debug field tests: periodic battery sample (percent, charging). */
    fun logBattery(percent: Int, charging: Boolean) {
        log?.event(now(), "battery", "level=$percent;charging=$charging")
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
        routeReports = null
        announcer = ReportAnnouncer()
        passedReports.reset()
        carAlongM = 0.0
        camerasJob?.cancel()
        cameras = emptyList()
        announcedCameras.clear()
        schoolsJob?.cancel()
        schools = emptyList()
        schoolRoute = null
        schoolAlerts.reset()
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
        // Community reports on the route ahead: a hint within 1 km, one spoken warning per report.
        val ahead = routeReports?.let { rr ->
            if (!progress.matched) null
            else {
                carAlongM = rr.totalMeters - progress.remainingMeters
                rr.ahead(com.roadpulse.app.data.ReportFeed.reports.value, carAlongM, REPORT_HINT_M)
            }
        }
        ahead?.let { noticePassedReports(it) }
        val camera = if (progress.matched) camerasAhead().firstOrNull() else null
        val cameraDue = camera != null && progress.prompt == null && camera.metersAhead <= CAMERA_WARN_M && announcedCameras.add(camera.id)
        if (camera != null && cameraDue) {
            log?.event(fix.timeMillis, "camera_ahead", "m=${camera.metersAhead.toInt()}")
            speak(VoiceSettings.narrator().cameraAhead(ReportAnnouncer.spokenMeters(camera.metersAhead), camera.maxSpeedKph), fix.timeMillis)
        }
        val limitHere = if (progress.matched) speedLimits.at(progress.segmentIndex) else current.speedLimitKph
        val school = if (progress.matched) schoolsAhead().firstOrNull() else null
        if (school != null && progress.prompt == null && !cameraDue && schoolAlerts.shouldWarn(school, carAlongM, limitHere)) {
            log?.event(fix.timeMillis, "school_ahead", "m=${school.metersAhead.toInt()}")
            speak(VoiceSettings.narrator().schoolZoneAhead(ReportAnnouncer.spokenMeters(school.metersAhead)), fix.timeMillis)
        }
        checkSpeeding(fix.speedMps, limitHere)
        if (progress.prompt == null) { // never talk over a turn instruction
            ahead?.let(announcer::next)?.let { due ->
                log?.event(fix.timeMillis, "report_ahead", "type=${due.report.type};m=${due.metersAhead.toInt()}")
                speak(VoiceSettings.narrator().reportAhead(due.report.type, ReportAnnouncer.spokenMeters(due.metersAhead)), fix.timeMillis)
            }
        }
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
                reportAhead = if (progress.matched) ahead?.firstOrNull() else it.reportAhead,
                cameraAhead = if (progress.matched) camera else it.cameraAhead,
                lanes = progress.upcoming?.lanes.orEmpty(),
                schoolAhead = if (progress.matched) school else it.schoolAhead,
            )
        }
    }

    private fun applyRoute(route: Route, destination: Place, resumed: Boolean) {
        tracker = RouteTracker(route, resumed, VoiceSettings::narrator)
        routeReports = RouteReports(route.shape)
        passedReports.newRoute() // reports no longer on the new route were not "passed"
        fetchCameras(route)
        fetchSchools(route)
        if (!resumed && _state.value == null) announcer = ReportAnnouncer() // a reroute keeps what was already said
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
                val route = router.route(from, current.destination.location, remainingStops(), com.roadpulse.app.data.RouteSettings.options.value)
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

    /** Loads the speed cameras along [route] in the background (needs the RoadPulse backend). */
    private fun fetchCameras(route: com.roadpulse.app.data.Route) {
        camerasJob?.cancel()
        cameras = emptyList()
        val url = com.roadpulse.app.data.ServerSettings.backendUrl()
        if (url.isEmpty()) return
        camerasJob = scope.launch {
            try {
                val found = com.roadpulse.app.data.PlacesClient(url).alongRoute(com.roadpulse.app.data.PlaceCategory.SPEED_CAMERA, route.shape)
                if (_state.value?.route === route) cameras = found
                log?.event(now(), "speed_cameras", "count=${found.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.event(now(), "speed_cameras", "failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Loads the schools along [route] in the background (needs the RoadPulse backend). */
    private fun fetchSchools(route: com.roadpulse.app.data.Route) {
        schoolsJob?.cancel()
        schools = emptyList()
        schoolRoute = RouteReports(route.shape, corridorM = SCHOOL_CORRIDOR_M)
        schoolAlerts.reset() // positions along the old route no longer apply
        val url = com.roadpulse.app.data.ServerSettings.backendUrl()
        if (url.isEmpty()) return
        schoolsJob = scope.launch {
            try {
                val found = com.roadpulse.app.data.PlacesClient(url)
                    .alongRoute(com.roadpulse.app.data.PlaceCategory.SCHOOL_ZONE, route.shape, corridorM = SCHOOL_CORRIDOR_M)
                if (_state.value?.route === route) schools = found
                log?.event(now(), "schools", "count=${found.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.event(now(), "schools", "failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun schoolsAhead(): List<SchoolAhead> {
        val rr = schoolRoute ?: return emptyList()
        return schools.mapNotNull { s ->
            val along = rr.alongOfPoint("school:" + s.id, s.location) ?: return@mapNotNull null
            val ahead = along - carAlongM
            if (ahead > 0 && ahead <= SCHOOL_HINT_M) SchoolAhead(s.id, ahead, along) else null
        }.sortedBy { it.metersAhead }
    }

    private fun camerasAhead(): List<CameraAhead> {
        val rr = routeReports ?: return emptyList()
        return cameras.mapNotNull { c ->
            val along = rr.alongOfPoint("cam:" + c.id, c.location) ?: return@mapNotNull null
            val ahead = along - carAlongM
            if (ahead > 0 && ahead <= REPORT_HINT_M) CameraAhead(c.id, ahead, c.maxSpeedKph) else null
        }.sortedBy { it.metersAhead }
    }

    /** Stops on the current route that the car has not reached yet. */
    private fun remainingStops(): List<GeoPoint> {
        val route = _state.value?.route ?: return emptyList()
        val rr = routeReports ?: return route.stops
        return route.stops.filterIndexed { i, _ ->
            val index = route.stopShapeIndexes.getOrNull(i) ?: return@filterIndexed true
            rr.alongAt(index) > carAlongM + STOP_PASSED_M
        }
    }

    /**
     * Adds a stop on the way during a drive: plans from [from] via [stop] (first), then the
     * remaining stops, to the destination. Returns false if no drive is active.
     */
    fun addStop(stop: GeoPoint, from: GeoPoint): Boolean {
        val current = _state.value ?: return false
        val stops = listOf(stop) + remainingStops()
        rerouteJob?.cancel()
        _state.value = current.copy(rerouting = true)
        log?.event(now(), "stop_added", "stops=${stops.size}")
        rerouteJob = scope.launch {
            try {
                val route = router.route(from, current.destination.location, stops, com.roadpulse.app.data.RouteSettings.options.value)
                if (_state.value == null) return@launch
                applyRoute(route, current.destination, resumed = false)
                speak(route.maneuvers.firstOrNull()?.let(VoiceSettings.narrator()::atTurn))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log?.event(now(), "stop_add_failed", e.javaClass.simpleName)
                _state.update { it?.copy(rerouting = false, offline = e is IOException) }
            }
        }
        return true
    }

    /** Emits [NavEvent.PassedReport] once for each report the car comes close to and then passes. */
    private fun noticePassedReports(ahead: List<ReportAhead>) {
        passedReports.update(ahead).forEach { id ->
            com.roadpulse.app.data.ReportFeed.reports.value.firstOrNull { it.id == id }?.let { _events.tryEmit(NavEvent.PassedReport(it)) }
        }
    }

    /** Emits [NavEvent.SpeedingAlert] when the speed first goes over limit + margin (at most every 20 s). */
    private fun checkSpeeding(speedMps: Double?, limitKph: Int?) {
        val kmh = speedMps?.let { (it * 3.6).toInt() } ?: return
        val speeding = com.roadpulse.app.ui.isSpeeding(kmh, limitKph, DisplaySettings.speedingMarginKmh.value)
        if (speeding && !wasSpeeding && DisplaySettings.speedingSound.value && now() - lastSpeedingAlert > SPEEDING_ALERT_GAP_MS) {
            lastSpeedingAlert = now()
            log?.event(now(), "speeding_alert", "kmh=$kmh;limit=$limitKph")
            _events.tryEmit(NavEvent.SpeedingAlert)
        }
        wasSpeeding = speeding
    }

    private const val STOP_PASSED_M = 30.0
    private const val CAMERA_WARN_M = 500.0
    private const val SCHOOL_CORRIDOR_M = 120.0
    private const val SCHOOL_HINT_M = 600.0
    private const val PASS_NEAR_M = 80.0
    private const val SPEEDING_ALERT_GAP_MS = 20_000L
    private const val REROUTE_MIN_INTERVAL_MS = 10_000L

    /** Reports this far ahead on the route are shown as a hint (and warned about from 800 m). */
    private const val REPORT_HINT_M = 1000.0
}
