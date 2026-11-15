package com.roadpulse.app.ios

import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.RouteOptions
import com.roadpulse.app.nav.DrivePlatform
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.NavEvent
import com.roadpulse.app.nav.NavState
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.NavigationSession
import com.roadpulse.app.nav.RouteMatcher
import com.roadpulse.app.nav.TurnIcon
import com.roadpulse.app.nav.narratorFor
import com.roadpulse.app.util.mathRound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One lane before the next turn: its arrow ([TurnIcon] name) and how good it is. */
class LaneArrow(val icon: String, val recommended: Boolean, val usable: Boolean)

/** The drive right now, in simple types for the iPhone screens. Numbers are -1 when unknown. */
class DriveSnapshot(
    val instruction: String,
    val distanceToTurnM: Double,
    /** Arrow for the next turn: a [TurnIcon] name such as "RIGHT" or "ROUNDABOUT". */
    val turnIcon: String,
    val remainingKm: Double,
    val remainingMinutes: Int,
    val speedKmh: Int,
    val speedLimitKph: Int,
    /** "Finding a new route…", "GPS signal lost…" and so on; null when all is well. */
    val status: String?,
    val hasPosition: Boolean,
    val lat: Double,
    val lon: Double,
    /** Direction of travel in degrees from north, -1 when unknown. */
    val bearingDeg: Double,
    val lanes: List<LaneArrow>,
    val routeShape: List<GeoPoint>,
)

/**
 * Turn-by-turn navigation for the iPhone app: starts and stops the shared drive session
 * ([NavigationSession], the same one Android uses), feeds it GPS fixes, and reports state and
 * the sentences to speak. Call everything from the main thread.
 */
class DriveController(private val routingUrl: String) {
    /** Language of the instructions on screen and spoken. */
    var language: GuidanceLanguage = GuidanceLanguage.EN
    private var simulated = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observers: Job? = null
    private var simulation: Job? = null
    private var lastPosition: GeoPoint? = null
    private var lastBearing = -1.0

    private val platform = object : DrivePlatform {
        override fun narrator(): Narrator = narratorFor(language)
        override fun routingUrl(): String = this@DriveController.routingUrl
        override fun backendUrl(): String = ""
        override fun routeOptions() = RouteOptions()
        override fun isSimulated(): Boolean = simulated
    }

    val isDriving: Boolean get() = NavigationSession.state.value != null

    /** Reports every change of the drive, each sentence to speak, and the arrival. */
    fun observe(onState: (DriveSnapshot?) -> Unit, onSpeak: (String) -> Unit, onArrived: () -> Unit) {
        observers?.cancel()
        observers = scope.launch {
            launch { NavigationSession.state.collect { onState(it?.let(::snapshot)) } }
            launch {
                NavigationSession.events.collect { e ->
                    when (e) {
                        is NavEvent.Speak -> onSpeak(e.text)
                        is NavEvent.Arrived -> onArrived()
                        else -> Unit
                    }
                }
            }
        }
    }

    fun start(route: RouteSummary, destinationName: String, destLat: Double, destLon: Double) {
        simulation?.cancel()
        simulated = false
        lastPosition = null
        lastBearing = -1.0
        NavigationSession.platform = platform
        NavigationSession.start(route.route, Place(destinationName, "", GeoPoint(destLat, destLon)), persistDrive = false)
    }

    /** Like [start], but the car drives the route by itself at [speedKmh] (for trying it at a desk). */
    fun startSimulated(route: RouteSummary, destinationName: String, destLat: Double, destLon: Double, speedKmh: Double) {
        start(route, destinationName, destLat, destLon)
        simulated = true
        val shape = route.route.shape
        val stepM = speedKmh / 3.6
        simulation = scope.launch {
            var i = 0
            var position = shape.first()
            while (i < shape.size - 1 && isDriving) {
                // Move stepM metres along the line.
                var left = stepM
                while (i < shape.size - 1) {
                    val d = Geo.distanceMeters(position, shape[i + 1])
                    if (d > left) {
                        val t = left / d
                        position = GeoPoint(position.lat + (shape[i + 1].lat - position.lat) * t, position.lon + (shape[i + 1].lon - position.lon) * t)
                        break
                    }
                    left -= d
                    i++
                    position = shape[i]
                }
                feed(position, 5.0, -1.0, stepM)
                delay(1_000)
            }
        }
    }

    fun stop() {
        simulation?.cancel()
        NavigationSession.stop()
    }

    /** A GPS fix from the phone. Pass -1 for values the phone does not report. Ignored while simulating. */
    fun onLocation(lat: Double, lon: Double, accuracyM: Double, bearingDeg: Double, speedMps: Double) {
        if (simulated) return
        feed(GeoPoint(lat, lon), accuracyM, bearingDeg, speedMps)
    }

    private fun feed(position: GeoPoint, accuracyM: Double, bearingDeg: Double, speedMps: Double) {
        if (!isDriving) return
        NavigationSession.onLocation(
            LocationFix(
                position,
                accuracyM = accuracyM.takeIf { it >= 0 },
                bearingDeg = bearingDeg.takeIf { it >= 0 },
                speedMps = speedMps.takeIf { it >= 0 },
                timeMillis = NavigationSession.now(),
            )
        )
    }

    private fun snapshot(s: NavState): DriveSnapshot {
        val n = narratorFor(language)
        val p = s.displayPosition
        val previous = lastPosition
        if (p != null && previous != null && Geo.distanceMeters(previous, p) > 3) lastBearing = RouteMatcher.bearing(previous, p)
        if (p != null && (previous == null || Geo.distanceMeters(previous, p) > 3)) lastPosition = p
        return DriveSnapshot(
            instruction = s.instruction ?: n.followRoute,
            distanceToTurnM = s.distanceToTurnMeters ?: -1.0,
            turnIcon = TurnIcon.forType(s.upcomingType).name,
            remainingKm = s.remainingKm,
            remainingMinutes = mathRound(s.remainingSeconds / 60).toInt(),
            speedKmh = s.speedMps?.let { mathRound(it * 3.6).toInt() } ?: -1,
            speedLimitKph = s.speedLimitKph ?: -1,
            status = when {
                s.rerouting -> n.statusFindingRoute
                s.offline -> n.statusOffline
                s.gpsLost -> n.statusGpsLost
                s.poorSignal -> n.statusWeakGps
                else -> null
            },
            hasPosition = p != null,
            lat = p?.lat ?: 0.0,
            lon = p?.lon ?: 0.0,
            bearingDeg = lastBearing,
            lanes = if (com.roadpulse.app.data.Lane.worthShowing(s.lanes)) {
                s.lanes.map { LaneArrow(TurnIcon.forLane(it.arrow).name, it.recommended, it.usable) }
            } else emptyList(),
            routeShape = s.route.shape,
        )
    }
}
