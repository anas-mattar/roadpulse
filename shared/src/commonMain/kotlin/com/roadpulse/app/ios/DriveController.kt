package com.roadpulse.app.ios

import com.roadpulse.app.data.DeviceApi
import com.roadpulse.app.data.Geo
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportsClient
import com.roadpulse.app.data.TrafficClient
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
import com.roadpulse.app.nav.ShareSession
import com.roadpulse.app.nav.TrafficSegmenter
import com.roadpulse.app.nav.TurnIcon
import com.roadpulse.app.nav.narratorFor
import com.roadpulse.app.i18n.stringsFor
import com.roadpulse.app.util.mathRound
import kotlin.coroutines.cancellation.CancellationException
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
    /** Hints on the route ahead (text in the guidance language), or null. Distances in metres, -1 when none. */
    val cameraLabel: String?,
    val cameraAheadM: Double,
    val schoolLabel: String?,
    val schoolAheadM: Double,
    val reportLabel: String?,
    val reportColorHex: String?,
    val reportAheadM: Double,
    /** Extra minutes from live traffic on the route ahead (0 when none or unknown). */
    val trafficDelayMinutes: Int,
    /** A faster route around heavy traffic: minutes it saves, or 0 when none is offered. */
    val fasterRouteSavesMinutes: Int,
    /** The car just passed a report: ask "Police still there?" (null when not asking). */
    val stillThereId: String?,
    val stillThereQuestion: String?,
    val stillThereColorHex: String?,
    /** The drive is shared with a live link. */
    val sharing: Boolean,
)

/** Reports sent from this phone (no "still there?" about your own reports). */
object OwnReports {
    val ids = mutableSetOf<String>()
}

/**
 * Turn-by-turn navigation for the iPhone app: starts and stops the shared drive session
 * ([NavigationSession], the same one Android uses), feeds it GPS fixes, and reports state and
 * the sentences to speak. Call everything from the main thread.
 */
class DriveController(var routingUrl: String) {
    /** The RoadPulse server for cameras, schools, reports and traffic; empty for none. */
    var backendUrl: String = ""
    /** Language of the instructions on screen and spoken. */
    var language: GuidanceLanguage = GuidanceLanguage.EN
    private var simulated = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observers: Job? = null
    private var simulation: Job? = null
    private var lastPosition: GeoPoint? = null
    private var lastBearing = -1.0
    private var lastState: NavState? = null
    private var onStateCallback: ((DriveSnapshot?) -> Unit)? = null
    private var stillThere: Report? = null
    private var stillThereJob: Job? = null
    private val askedAbout = mutableSetOf<String>()

    /** The driver agreed to help with live traffic (anonymous road pieces, ADR 0004). Off by default. */
    var contributeTraffic: Boolean = false
        set(value) {
            field = value
            if (!value) segmenter.reset()
        }
    private val segmenter = TrafficSegmenter()

    private val platform = object : DrivePlatform {
        override fun narrator(): Narrator = narratorFor(language)
        override fun routingUrl(): String = this@DriveController.routingUrl
        override fun backendUrl(): String = this@DriveController.backendUrl
        override fun routeOptions() = RouteOptions()
        override fun isSimulated(): Boolean = simulated
        override fun trafficFix(position: GeoPoint, speedMps: Double?, timeMillis: Long, remainingM: Double) {
            val url = backendUrl
            if (!contributeTraffic || url.isEmpty()) return
            val segment = segmenter.add(position, speedMps, timeMillis, remainingM) ?: return
            // Sent once; on failure it is dropped (no queue of old positions on the phone).
            scope.launch { runCatching { TrafficClient(url).upload(segment) } }
        }
        override fun driveEnded() = segmenter.reset()
        override fun onArrived() = ShareSession.onArrived()
    }

    val isDriving: Boolean get() = NavigationSession.state.value != null

    /** Reports every change of the drive, each sentence to speak, and the arrival. */
    fun observe(onState: (DriveSnapshot?) -> Unit, onSpeak: (String) -> Unit, onArrived: () -> Unit) {
        observers?.cancel()
        onStateCallback = onState
        observers = scope.launch {
            launch {
                NavigationSession.state.collect {
                    lastState = it
                    if (it == null) clearStillThere()
                    onState(it?.let(::snapshot))
                }
            }
            launch {
                NavigationSession.events.collect { e ->
                    when (e) {
                        is NavEvent.Speak -> onSpeak(e.text)
                        is NavEvent.Arrived -> onArrived()
                        is NavEvent.PassedReport -> askStillThere(e.report)
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

    /** The driver took the offered faster route, or kept the current one. */
    fun takeFasterRoute() = NavigationSession.acceptFasterRoute()
    fun keepRoute() = NavigationSession.declineFasterRoute()

    /** Answer to "still there?": true, false, or null for Skip. */
    fun answerStillThere(stillThere: Boolean?) {
        val report = this.stillThere ?: return
        clearStillThere()
        refresh()
        val url = backendUrl
        if (stillThere == null || url.isEmpty()) return
        scope.launch { runCatching { ReportsClient(url, DeviceApi(url, tokenStoreFor(url))).vote(report.id, stillThere) } }
    }

    private fun askStillThere(report: Report) {
        if (backendUrl.isEmpty() || report.id in askedAbout || report.id in OwnReports.ids) return
        askedAbout += report.id
        stillThere = report
        stillThereJob?.cancel()
        stillThereJob = scope.launch {
            delay(STILL_THERE_SHOWN_MS)
            if (stillThere?.id == report.id) {
                stillThere = null
                refresh()
            }
        }
        refresh()
    }

    private fun clearStillThere() {
        stillThere = null
        stillThereJob?.cancel()
    }

    private fun refresh() {
        val s = lastState ?: return
        onStateCallback?.invoke(snapshot(s))
    }

    /**
     * Shares the drive with a live link (needs the laptop server); returns the link. [name] is shown
     * on the page ("Anas is on the way"), or null.
     */
    @Throws(Exception::class, CancellationException::class)
    suspend fun startSharing(name: String?): String {
        val url = backendUrl
        val link = ShareSession.start(url, tokenStoreFor(url), name)
        refresh()
        return link.url
    }

    fun stopSharing() {
        ShareSession.stop()
        refresh()
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
            cameraLabel = s.cameraAhead?.let { c -> n.speedCamera + (c.maxSpeedKph?.let { " $it" } ?: "") },
            cameraAheadM = s.cameraAhead?.metersAhead ?: -1.0,
            schoolLabel = s.schoolAhead?.let { n.schoolZone },
            schoolAheadM = s.schoolAhead?.metersAhead ?: -1.0,
            reportLabel = s.reportAhead?.let { n.reportName(it.report.type) },
            reportColorHex = s.reportAhead?.report?.type?.colorHex,
            reportAheadM = s.reportAhead?.metersAhead ?: -1.0,
            trafficDelayMinutes = mathRound(s.trafficDelaySeconds / 60).toInt(),
            fasterRouteSavesMinutes = s.fasterRoute?.let { mathRound(it.savedSeconds / 60).toInt() } ?: 0,
            stillThereId = stillThere?.id,
            stillThereQuestion = stillThere?.let { stringsFor(language).stillThereQuestion(it.type) },
            stillThereColorHex = stillThere?.type?.colorHex,
            sharing = ShareSession.link.value != null,
        )
    }

    private companion object {
        const val STILL_THERE_SHOWN_MS = 12_000L
    }
}
