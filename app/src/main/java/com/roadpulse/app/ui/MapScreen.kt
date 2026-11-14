package com.roadpulse.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.Gravity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadpulse.app.BuildConfig
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.Route
import com.roadpulse.app.nav.DeviceLocation
import com.roadpulse.app.nav.DisplaySettings
import com.roadpulse.app.nav.MapColors
import com.roadpulse.app.nav.SunTimes
import com.roadpulse.app.nav.DriveReplay
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.GuidanceLanguage
import com.roadpulse.app.nav.Narrator
import com.roadpulse.app.nav.VoiceProbe
import com.roadpulse.app.nav.VoiceSettings
import com.roadpulse.app.nav.narratorFor
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

private const val ROUTE_SOURCE = "route-source"
private const val DEST_SOURCE = "destination-source"
private const val REPORT_SOURCE = "report-source"
private const val MATCHED_SOURCE = "matched-source"

// Kuala Lumpur; shown until the first GPS fix arrives.
private val DEFAULT_CENTER = LatLng(3.139, 101.6869)

@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var showReportDialog by remember { mutableStateOf(false) }
    val voiceMuted by VoiceSettings.muted.collectAsStateWithLifecycle()
    val effectiveLanguage by VoiceSettings.effectiveLanguage.collectAsStateWithLifecycle()
    val narrator = narratorFor(effectiveLanguage)
    var showVoiceSettings by remember { mutableStateOf(false) }
    var showSimulateDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasLocationPermission = context.hasLocationPermission() }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            // Needed on Android 13+ to show the ongoing-navigation notification.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions += Manifest.permission.POST_NOTIFICATIONS
            }
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // Our own multi-provider location feed (see DeviceLocation); MapLibre's puck is driven from it.
    var deviceFix by remember { mutableStateOf<GeoPoint?>(null) }
    DisposableEffect(hasLocationPermission) {
        val source = DeviceLocation(context)
        if (hasLocationPermission) {
            source.start { location ->
                if (DriveReplay.active.value) return@start // simulated drive owns the position
                deviceFix = GeoPoint(location.latitude, location.longitude)
                map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(location)
            }
        }
        onDispose { source.stop() }
    }

    // Debug: during a simulated drive, show the simulated position as the location puck.
    LaunchedEffect(map) {
        DriveReplay.fixes.collect { fix ->
            deviceFix = fix.position
            map?.locationComponent?.takeIf { it.isLocationComponentActivated }
                ?.forceLocationUpdate(fix.toAndroidLocation())
        }
    }

    fun currentLocation(): GeoPoint? = deviceFix ?: map?.lastLocation(hasLocationPermission)

    // Search near the user; before the first GPS fix, near the area shown on the map.
    fun searchBias(): GeoPoint? = currentLocation()
        ?: map?.cameraPosition?.target?.let { GeoPoint(it.latitude, it.longitude) }

    // Centre on the user once, when the first position arrives.
    var centredOnUser by remember { mutableStateOf(false) }
    LaunchedEffect(map, deviceFix) {
        val m = map ?: return@LaunchedEffect
        val p = deviceFix ?: return@LaunchedEffect
        if (!centredOnUser && !state.navigating && state.route == null) {
            centredOnUser = true
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 14.5))
        }
    }

    // Enable the blue location puck once both the style and the permission are ready.
    LaunchedEffect(map, style, hasLocationPermission) {
        val m = map ?: return@LaunchedEffect
        val s = style ?: return@LaunchedEffect
        if (hasLocationPermission) m.enableLocation(context, s)
    }

    // Push view-model state into the map's GeoJSON sources.
    LaunchedEffect(style, state.route) {
        style?.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(state.route.toFeatureCollection())
        val route = state.route
        val m = map
        if (route != null && m != null && route.shape.size >= 2 && !state.navigating) {
            val bounds = LatLngBounds.Builder()
                .includes(route.shape.map { LatLng(it.lat, it.lon) })
                .build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }
    LaunchedEffect(style, state.destination) {
        style?.getSourceAs<GeoJsonSource>(DEST_SOURCE)?.setGeoJson(state.destination.toFeatureCollection())
    }
    // Route-matched position while navigating (the default puck still shows the raw GPS fix).
    LaunchedEffect(style, state.matchedPosition) {
        val p = state.matchedPosition
        style?.getSourceAs<GeoJsonSource>(MATCHED_SOURCE)?.setGeoJson(
            if (p == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))
        )
    }
    LaunchedEffect(style, state.reports) {
        style?.getSourceAs<GeoJsonSource>(REPORT_SOURCE)?.setGeoJson(state.reports.toFeatureCollection())
    }

    // Navigation mode: follow the car with a tilted camera. Re-applied after every map style load
    // (a style switch resets it) and whenever the driver taps Re-center after panning the map.
    var following by remember { mutableStateOf(true) }
    var trackingListenerAdded by remember { mutableStateOf(false) }
    LaunchedEffect(state.navigating) { if (state.navigating) following = true }
    LaunchedEffect(state.navigating, map, style, hasLocationPermission, following) {
        val m = map ?: return@LaunchedEffect
        if (style == null || !hasLocationPermission || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
        val lc = m.locationComponent
        if (!trackingListenerAdded) {
            trackingListenerAdded = true
            lc.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {
                    if (state.navigating) following = false // the driver panned or zoomed the map
                }
                override fun onCameraTrackingChanged(currentMode: Int) = Unit
            })
        }
        if (state.navigating && following) {
            lc.renderMode = RenderMode.GPS
            lc.cameraMode = CameraMode.TRACKING_GPS
            lc.zoomWhileTracking(16.5)
            lc.tiltWhileTracking(50.0)
        } else if (!state.navigating) {
            lc.cameraMode = CameraMode.NONE
            lc.renderMode = RenderMode.COMPASS
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val density = LocalDensity.current
    val mode = when {
        state.navigating -> ScreenMode.DRIVING
        state.route != null || state.busy && state.destination != null -> ScreenMode.PREVIEW
        else -> ScreenMode.EXPLORE
    }
    // Light map by day; the night style after sunset (or as chosen in settings).
    val mapColors by DisplaySettings.mapColors.collectAsStateWithLifecycle()
    var isNight by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val p = currentLocation() ?: GeoPoint(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude)
            isNight = SunTimes.isNight(p.lat, p.lon, System.currentTimeMillis())
            delay(60_000)
        }
    }
    val darkMap = mapColors == MapColors.DARK || (mapColors == MapColors.AUTO && isNight)
    val styleUrl = if (darkMap) BuildConfig.MAP_STYLE_DARK_URL else BuildConfig.MAP_STYLE_URL

    // While driving, keep the car in the lower part of the screen, below the turn card.
    LaunchedEffect(map, mode) {
        val m = map ?: return@LaunchedEffect
        val top = if (mode == ScreenMode.DRIVING) with(density) { 260.dp.toPx().toDouble() } else 0.0
        m.cameraPosition = CameraPosition.Builder(m.cameraPosition).padding(0.0, top, 0.0, 0.0).build()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = RP.Field) { _ ->
        Box(Modifier.fillMaxSize()) {
            MapLibreView(
                styleUrl = styleUrl,
                onMapReady = { m -> map = m },
                onStyleChanging = { style = null },
                onStyleLoaded = { s -> style = s },
            )

            when (mode) {
                ScreenMode.EXPLORE -> ExploreOverlay(
                    query = state.query,
                    results = state.searchResults,
                    onQueryChange = { viewModel.onQueryChange(it, searchBias()) },
                    onClear = viewModel::clearRoute,
                    onSelect = { viewModel.onPlaceSelected(it, currentLocation()) },
                    onOpenSettings = { showVoiceSettings = true },
                    onRecenter = {
                        currentLocation()?.let { p ->
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 15.0))
                        }
                    },
                    onReport = { showReportDialog = true },
                )

                ScreenMode.PREVIEW -> PreviewOverlay(
                    destination = state.destination,
                    route = state.route,
                    busy = state.busy,
                    onBack = viewModel::clearRoute,
                    onGo = viewModel::startNavigation,
                    onSimulate = if (BuildConfig.DEBUG) ({ showSimulateDialog = true }) else null,
                )

                ScreenMode.DRIVING -> DrivingOverlay(
                    state = state,
                    narrator = narrator,
                    muted = voiceMuted,
                    onToggleMute = { VoiceSettings.setMuted(!voiceMuted) },
                    onReport = { showReportDialog = true },
                    onStop = viewModel::clearRoute,
                    following = following,
                    onRecenter = { following = true },
                )
            }
        }
    }

    if (showVoiceSettings) {
        VoiceSettingsDialog(onDismiss = { showVoiceSettings = false })
    }

    if (showSimulateDialog) {
        AlertDialog(
            onDismissRequest = { showSimulateDialog = false },
            title = { Text("Simulate a drive (debug)") },
            text = {
                Column {
                    DriveReplay.presets.forEach { preset ->
                        TextButton(
                            onClick = {
                                showSimulateDialog = false
                                viewModel.simulateDrive(preset)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(preset.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSimulateDialog = false }) { Text("Cancel") } },
        )
    }

    if (showReportDialog) {
        ReportDialog(
            onDismiss = { showReportDialog = false },
            onPick = { type ->
                showReportDialog = false
                viewModel.submitReport(type, currentLocation())
            },
        )
    }
}

@Composable
private fun MapLibreView(
    styleUrl: String,
    onMapReady: (MapLibreMap) -> Unit,
    onStyleChanging: () -> Unit,
    onStyleLoaded: (Style) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(DEFAULT_CENTER).zoom(11.0).build()
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            // Map data credits are shown as text in the bottom panels; keep the (i) details button
            // at the top-left, clear of the panels.
            m.uiSettings.attributionGravity = Gravity.TOP or Gravity.START
            m.uiSettings.setAttributionMargins(24, 160, 0, 0)
            map = m
            onMapReady(m)
        }
    }

    // Loads the light style, and switches to the dark one while driving. Our route and marker
    // layers are added again on every style load.
    LaunchedEffect(map, styleUrl) {
        val m = map ?: return@LaunchedEffect
        onStyleChanging()
        m.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
            style.addOverlayLayers()
            onStyleLoaded(style)
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun Style.addOverlayLayers() {
    addSource(GeoJsonSource(ROUTE_SOURCE))
    addSource(GeoJsonSource(DEST_SOURCE))
    addSource(GeoJsonSource(REPORT_SOURCE))
    addSource(GeoJsonSource(MATCHED_SOURCE))

    addLayer(
        LineLayer("route-casing", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#3C3489"),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    addLayer(
        LineLayer("route-line", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#7F77DD"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    addLayer(
        CircleLayer("destination", DEST_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor("#26215C"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    addLayer(
        CircleLayer("matched-position", MATCHED_SOURCE).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor("#534AB7"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(4f),
        )
    )
    addLayer(
        CircleLayer("reports", REPORT_SOURCE).withProperties(
            PropertyFactory.circleRadius(11f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.enableLocation(context: Context, style: Style) {
    val component = locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(false).build()
        )
    }
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.COMPASS
    component.lastKnownLocation?.let {
        animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14.5))
    }
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.lastLocation(hasPermission: Boolean): GeoPoint? {
    if (!hasPermission || !locationComponent.isLocationComponentActivated) return null
    return locationComponent.lastKnownLocation?.let { GeoPoint(it.latitude, it.longitude) }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun Route?.toFeatureCollection(): FeatureCollection {
    if (this == null || shape.size < 2) return FeatureCollection.fromFeatures(emptyList())
    val line = LineString.fromLngLats(shape.map { Point.fromLngLat(it.lon, it.lat) })
    return FeatureCollection.fromFeature(Feature.fromGeometry(line))
}

private fun Place?.toFeatureCollection(): FeatureCollection {
    if (this == null) return FeatureCollection.fromFeatures(emptyList())
    return FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(location.lon, location.lat)))
}

private fun List<Report>.toFeatureCollection(): FeatureCollection =
    FeatureCollection.fromFeatures(
        map { report ->
            Feature.fromGeometry(Point.fromLngLat(report.location.lon, report.location.lat)).apply {
                addStringProperty("id", report.id)
                addStringProperty("type", report.type.name)
                addStringProperty("color", report.type.colorHex)
            }
        }
    )

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onPick: (ReportType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What do you see?") },
        text = {
            Column {
                ReportType.entries.forEach { type ->
                    TextButton(onClick = { onPick(type) }, modifier = Modifier.fillMaxWidth()) {
                        Text(type.label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun formatKm(km: Double): String =
    if (km < 1) "${(km * 1000).roundToInt()} m" else "%.1f km".format(km)

internal fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** Arrival clock time in the phone's own 12/24-hour style. */
internal fun formatArrival(context: Context, secondsFromNow: Double): String =
    android.text.format.DateFormat.getTimeFormat(context)
        .format(java.util.Date(System.currentTimeMillis() + (secondsFromNow * 1000).toLong()))

private fun LocationFix.toAndroidLocation(): android.location.Location =
    android.location.Location("replay").also { l ->
        l.latitude = position.lat
        l.longitude = position.lon
        accuracyM?.let { l.accuracy = it.toFloat() }
        bearingDeg?.let { l.bearing = it.toFloat() }
        speedMps?.let { l.speed = it.toFloat() }
        l.time = System.currentTimeMillis()
        l.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
    }

@Composable
private fun VoiceSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val language by VoiceSettings.language.collectAsStateWithLifecycle()
    val missing by VoiceSettings.missingVoices.collectAsStateWithLifecycle()
    val muted by VoiceSettings.muted.collectAsStateWithLifecycle()
    val mapColors by DisplaySettings.mapColors.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                Text("Voice language", style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary)
                GuidanceLanguage.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                VoiceSettings.setLanguage(option)
                                VoiceProbe.check(context, option)
                            },
                    ) {
                        RadioButton(selected = language == option, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (language in missing) {
                    Text(
                        "No ${language.label} voice is installed on this phone, so guidance stays in English. " +
                            "Install one in Android Settings > System > Languages > Text-to-speech output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Text("Mute voice", modifier = Modifier.weight(1f))
                    Switch(checked = muted, onCheckedChange = VoiceSettings::setMuted)
                }
                Text(
                    "Map colours",
                    style = MaterialTheme.typography.labelLarge,
                    color = RP.TextSecondary,
                    modifier = Modifier.padding(top = 16.dp),
                )
                MapColors.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { DisplaySettings.setMapColors(option) },
                    ) {
                        RadioButton(selected = mapColors == option, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
