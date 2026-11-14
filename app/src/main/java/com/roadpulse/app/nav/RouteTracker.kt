package com.roadpulse.app.nav

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Maneuver
import com.roadpulse.app.data.Route
import kotlin.math.roundToInt

data class Progress(
    /** Off route after hysteresis: when true, reroute. */
    val offRoute: Boolean,
    /** Raw distance between the latest fix and the route line. */
    val offRouteMeters: Double,
    /** Position to display: on the route line when matched, otherwise the raw fix. */
    val displayPosition: GeoPoint,
    val matched: Boolean,
    /** The latest fix was too inaccurate to use. */
    val poorSignal: Boolean,
    val upcoming: Maneuver?,
    val distanceToTurnMeters: Double,
    /** The maneuver after [upcoming], and the distance between the two (for "Then …" hints). */
    val following: Maneuver? = null,
    val followingGapMeters: Double? = null,
    val remainingMeters: Double,
    val remainingSeconds: Double,
    /** Voice prompt that became due with this position fix, if any. */
    val prompt: String?,
    val arrived: Boolean,
)

/**
 * Tracks progress along one route and decides when voice prompts are due.
 * Pure Kotlin (no Android types) so it can be unit tested.
 */
class RouteTracker(
    val route: Route,
    /** True when continuing a saved drive: the driver may be anywhere along the route. */
    resumed: Boolean = false,
    /** Language of prompts; read on every update so a language change applies immediately. */
    private val narrator: () -> Narrator = { EnglishNarrator },
) {

    private val matcher = RouteMatcher(route.shape, startAlongM = if (resumed) null else 0.0)
    private val offRouteDetector = OffRouteDetector()
    private val alerted = HashSet<Int>()
    private val announced = HashSet<Int>()
    private var last: Progress? = null
    private var syntheticClockMillis = 0L

    /** Convenience for tests: a good-accuracy fix without heading, one second after the last. */
    fun update(position: GeoPoint): Progress {
        syntheticClockMillis += 1000
        return update(LocationFix(position, accuracyM = 5.0, timeMillis = syntheticClockMillis))
    }

    fun update(fix: LocationFix): Progress {
        val poor = (fix.accuracyM ?: 0.0) > POOR_ACCURACY_M
        if (poor) {
            // Too inaccurate to move progress or to count towards off route: keep the last state.
            last?.let { return it.copy(prompt = null, poorSignal = true) }
        }

        val match = matcher.match(fix, commit = !poor)
        val offRoute = if (poor) offRouteDetector.offRoute else offRouteDetector.onFix(match.onRoute, fix.timeMillis)

        val along = match.distanceAlongM
        val remaining = (matcher.totalMeters - along).coerceAtLeast(0.0)
        val remainingSeconds =
            if (matcher.totalMeters > 0) route.timeSeconds * remaining / matcher.totalMeters else 0.0
        val arrived = match.onRoute && remaining <= ARRIVAL_RADIUS_M

        val upcomingIndex = route.maneuvers.indexOfFirst { alongAt(it) > along + 1.0 }
            .takeIf { it >= 0 } ?: route.maneuvers.lastIndex
        val upcoming = route.maneuvers.getOrNull(upcomingIndex)
        val distanceToTurn = upcoming?.let { (alongAt(it) - along).coerceAtLeast(0.0) } ?: remaining
        val following = route.maneuvers.getOrNull(upcomingIndex + 1)
        val followingGap = if (upcoming != null && following != null) alongAt(following) - alongAt(upcoming) else null

        var prompt: String? = null
        if (upcoming != null && match.onRoute && !poor) {
            if (distanceToTurn <= FINAL_PROMPT_M) {
                if (announced.add(upcomingIndex)) {
                    alerted.add(upcomingIndex)
                    prompt = narrator().atTurn(upcoming)
                }
            } else if (distanceToTurn <= ALERT_PROMPT_M && distanceToTurn > ALERT_MIN_M) {
                if (alerted.add(upcomingIndex)) {
                    val rounded = ((distanceToTurn / 50).roundToInt() * 50).coerceAtLeast(50)
                    prompt = narrator().alert(upcoming, rounded)
                }
            }
        }

        return Progress(
            offRoute = offRoute,
            offRouteMeters = match.offsetM,
            displayPosition = if (match.onRoute) match.snapped else fix.position,
            matched = match.onRoute,
            poorSignal = poor,
            upcoming = upcoming,
            distanceToTurnMeters = distanceToTurn,
            following = following,
            followingGapMeters = followingGap,
            remainingMeters = remaining,
            remainingSeconds = remainingSeconds,
            prompt = prompt,
            arrived = arrived,
        ).also { last = it }
    }

    private fun alongAt(m: Maneuver): Double =
        matcher.cumulative[m.beginShapeIndex.coerceIn(0, matcher.cumulative.lastIndex)]

    companion object {
        /** Used for display/diagnostics; the matcher's per-fix tolerance decides "on route". */
        const val OFF_ROUTE_M = RouteMatcher.BASE_TOLERANCE_M + RouteMatcher.DEFAULT_ACCURACY_M
        const val POOR_ACCURACY_M = 75.0
        const val ARRIVAL_RADIUS_M = 30.0
        const val ALERT_PROMPT_M = 500.0
        const val ALERT_MIN_M = 150.0
        const val FINAL_PROMPT_M = 80.0
    }
}
