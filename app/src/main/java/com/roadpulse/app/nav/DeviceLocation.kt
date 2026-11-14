package com.roadpulse.app.nav

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock

/**
 * Location updates from every useful provider at once (GPS, platform/Google fused, network).
 *
 * Relying on a single provider is fragile: on emulators and some phones the fused provider
 * never reports a position even though GPS has one. GPS fixes are preferred; the other providers
 * are used only when GPS has been silent for [GPS_PREFERENCE_MS].
 *
 * Callers must hold a location permission before calling [start].
 */
class DeviceLocation(context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var callback: ((Location) -> Unit)? = null
    private var lastGpsFixAt = 0L

    private val listener = LocationListener { location -> deliver(location) }

    @SuppressLint("MissingPermission")
    fun start(intervalMs: Long = 1000L, onLocation: (Location) -> Unit) {
        stop()
        callback = onLocation
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { it in manager.allProviders }

        for (provider in providers) {
            runCatching {
                manager.requestLocationUpdates(provider, intervalMs, 0f, listener, Looper.getMainLooper())
            }
        }
        // Start from the freshest cached position so the map can centre immediately.
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let(::deliver)
    }

    fun stop() {
        manager.removeUpdates(listener)
        callback = null
    }

    private fun deliver(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (location.provider == LocationManager.GPS_PROVIDER) {
            lastGpsFixAt = now
        } else if (lastGpsFixAt != 0L && now - lastGpsFixAt < GPS_PREFERENCE_MS) {
            return // GPS is active: ignore coarser providers to avoid jitter
        }
        callback?.invoke(location)
    }

    private companion object {
        const val GPS_PREFERENCE_MS = 5_000L
    }
}
