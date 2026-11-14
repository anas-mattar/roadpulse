package com.roadpulse.app.nav

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.roadpulse.app.BuildConfig
import com.roadpulse.app.MainActivity
import com.roadpulse.app.data.GeoPoint
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground service that keeps a drive alive when the app is in the background or the
 * screen is off: receives GPS fixes, advances [NavigationSession], and speaks prompts.
 */
class NavigationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var voice: VoiceGuide
    private lateinit var deviceLocation: DeviceLocation
    private var started = false

    private fun onLocation(location: Location) {
        NavigationSession.onLocation(
            LocationFix(
                position = GeoPoint(location.latitude, location.longitude),
                accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
                bearingDeg = if (location.hasBearing()) location.bearing.toDouble() else null,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else null,
                timeMillis = location.elapsedRealtimeNanos / 1_000_000,
            )
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        VoiceSettings.init(this)
        com.roadpulse.app.data.ServerSettings.init(this)
        voice = VoiceGuide(this)
        deviceLocation = DeviceLocation(this)
        NavigationSession.init(driveStore(this), SystemClock::elapsedRealtime)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            NavigationSession.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        // After Android stopped the app, the system restarts this sticky service with no intent:
        // continue the saved drive, if any.
        if (!hasLocationPermission() || !NavigationSession.restoreIfSaved()) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(NavigationSession.state.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Android may refuse a background restart; the saved drive resumes when the app is opened.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            val replay = DriveReplay.currentConfig?.takeIf { BuildConfig.DEBUG && DriveReplay.active.value }
            if (replay != null) {
                scope.launch { DriveReplay.fixes.collect(NavigationSession::onLocation) }
            } else {
                requestLocationUpdates()
            }
            if (BuildConfig.DEBUG) {
                openDriveLog(replay)
                // Battery sample every 5 minutes for the field-test battery-per-hour figure.
                scope.launch {
                    while (true) {
                        val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
                        NavigationSession.logBattery(
                            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY),
                            bm.isCharging,
                        )
                        delay(5 * 60_000L)
                    }
                }
            }

            scope.launch {
                NavigationSession.events.collect { event ->
                    if (event is NavEvent.Speak) voice.speak(event.text)
                }
            }
            NavigationSession.announceStart()
            scope.launch {
                VoiceSettings.muted.collect { muted -> if (muted) voice.stopSpeaking() }
            }
            scope.launch {
                VoiceSettings.effectiveLanguage.collect { voice.applyLanguage(it) }
            }
            scope.launch {
                NavigationSession.state.collect { state ->
                    if (state == null) {
                        delay(4000) // let the final prompt finish
                        stopSelf()
                    } else {
                        notificationManager().notify(NOTIFICATION_ID, buildNotification(state))
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        deviceLocation.stop()
        scope.cancel()
        voice.shutdown()
        super.onDestroy()
    }

    /** Debug builds: CSV log under Android/data/com.roadpulse.app/files/navlogs/. */
    private fun openDriveLog(replay: DriveReplay.Config?) {
        runCatching {
            val dir = File(getExternalFilesDir(null), "navlogs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val kind = if (replay != null) "replay" else "gps"
            NavigationSession.attachLog(NavLog(File(dir, "drive-$stamp-$kind.csv"), SystemClock::elapsedRealtime), replay)
        }
    }

    private fun requestLocationUpdates() {
        deviceLocation.start(1000L, ::onLocation)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Navigation", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shown while RoadPulse is guiding a drive"
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(state: NavState?): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, NavigationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val turn = state?.distanceToTurnMeters?.let { "In ${it.roundToInt()} m: " }.orEmpty()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(state?.destination?.name?.let { "Navigating to $it" } ?: "Navigation")
            .setContentText(turn + (state?.instruction ?: "Follow the route"))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.roadpulse.app.action.STOP_NAVIGATION"

        fun driveStore(context: Context) = DriveStore(File(context.filesDir, "active_drive.json"))

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, NavigationService::class.java))
        }
    }
}
