package com.roadpulse.app.nav

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RouteOptions
import kotlinx.serialization.Serializable

/**
 * What a phone provides to the shared drive session ([NavigationSession]): the voice language,
 * server addresses, route options, and hooks for features that live in the app (traffic opt-in,
 * share my drive). Android and iPhone each implement it.
 */
interface DrivePlatform {
    fun narrator(): Narrator
    fun routingUrl(): String
    /** Empty when there is no RoadPulse server (no cameras, schools, traffic). */
    fun backendUrl(): String
    fun routeOptions(): RouteOptions
    /** A simulated drive: never used for live traffic. */
    fun isSimulated(): Boolean = false
    /** A real GPS fix while navigating, for the opt-in traffic upload. */
    fun trafficFix(position: GeoPoint, speedMps: Double?, timeMillis: Long, remainingM: Double) {}
    fun driveEnded() {}
    fun onArrived() {}
    /** How far over the limit counts as speeding (km/h), and whether to alert with a sound. */
    fun speedingMarginKmh(): Int = 3
    fun speedingSound(): Boolean = true

    /** English prompts, no servers: used until the app installs its own. */
    object Default : DrivePlatform {
        override fun narrator(): Narrator = EnglishNarrator
        override fun routingUrl() = ""
        override fun backendUrl() = ""
        override fun routeOptions() = RouteOptions()
    }
}

@Serializable
data class SavedDrive(val route: Route, val destination: Place, val savedAtEpochMs: Long)

/** Keeps the active drive so navigation can continue after the system stops the app. */
interface DriveStorage {
    fun save(route: Route, destination: Place)
    fun load(): SavedDrive?
    fun clear()
}

/** Debug drive log (CSV on Android). */
interface DriveLog {
    fun fix(fix: LocationFix, p: Progress)
    fun event(timeMillis: Long, name: String, detail: String = "")
    fun close()
}
