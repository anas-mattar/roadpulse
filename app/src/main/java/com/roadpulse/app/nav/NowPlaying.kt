package com.roadpulse.app.nav

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The music another app is playing (Spotify, YouTube Music…), for the controls while driving. */
data class Track(val title: String, val artist: String?, val playing: Boolean, val app: String)

/**
 * Play/pause/next for the phone's music app, using Android's media sessions. Android only shares
 * other apps' media sessions with apps that have notification access, so the driver turns this on
 * in Settings and grants that access once. RoadPulse ignores the notifications themselves.
 */
object NowPlaying {
    private var prefs: SharedPreferences? = null
    private val _enabled = MutableStateFlow(false)
    /** The driver turned music controls on in Settings. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("roadpulse_settings", Context.MODE_PRIVATE)
        prefs = p
        _enabled.value = p.getBoolean(KEY, false)
    }

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun setEnabled(context: Context, on: Boolean) {
        _enabled.value = on
        prefs?.edit()?.putBoolean(KEY, on)?.apply()
        refresh(context)
    }

    /** Starts (or stops) following the active music session. Call on app resume and after a setting change. */
    fun refresh(context: Context) {
        if (!_enabled.value || !hasAccess(context)) {
            stop()
            return
        }
        if (sessionsListener != null) return
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, MediaAccessService::class.java)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }
        runCatching {
            m.addOnActiveSessionsChangedListener(listener, component)
            follow(m.getActiveSessions(component))
            manager = m
            sessionsListener = listener
        }
    }

    private fun stop() {
        sessionsListener?.let { l -> runCatching { manager?.removeOnActiveSessionsChangedListener(l) } }
        sessionsListener = null
        controller?.unregisterCallback(callback)
        controller = null
        _track.value = null
    }

    /** Follows the session that is playing, else the most recent one. */
    private fun follow(sessions: List<MediaController>) {
        val chosen = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback)
        }
        publish()
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            _track.value = null
        }
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        _track.value = if (c == null || title.isNullOrBlank()) null else Track(
            title = title,
            artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            app = c.packageName,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    private const val KEY = "music_controls"
}

/**
 * Needed only so Android lets RoadPulse see which music is playing (media sessions are shared with
 * notification-access apps). It does nothing with notifications.
 */
class MediaAccessService : NotificationListenerService()
