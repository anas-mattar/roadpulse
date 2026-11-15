package com.roadpulse.app.nav

import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.RouteSettings
import com.roadpulse.app.data.ServerSettings

/** Connects the shared drive session to the Android app's settings and features. */
object AndroidDrivePlatform : DrivePlatform {
    override fun narrator(): Narrator = VoiceSettings.narrator()
    // Read on each use, so the debug server switch also applies to reroutes.
    override fun routingUrl(): String = ServerSettings.routingUrl()
    override fun backendUrl(): String = ServerSettings.backendUrl()
    override fun routeOptions() = RouteSettings.options.value
    override fun isSimulated(): Boolean = DriveReplay.active.value
    override fun trafficFix(position: GeoPoint, speedMps: Double?, timeMillis: Long, remainingM: Double) =
        TrafficUploader.onFix(position, speedMps, timeMillis, remainingM)
    override fun driveEnded() = TrafficUploader.driveEnded()
    override fun onArrived() = ShareSession.onArrived()
    override fun speedingMarginKmh(): Int = DisplaySettings.speedingMarginKmh.value
    override fun speedingSound(): Boolean = DisplaySettings.speedingSound.value
}
