package com.roadpulse.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import com.roadpulse.app.nav.DriveReplay
import com.roadpulse.app.nav.LocationFix
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
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

    // Navigation mode: follow the user with a tilted camera. Progress, rerouting and voice
    // are handled by NavigationService so they keep working with the screen off.
    LaunchedEffect(state.navigating, map, hasLocationPermission) {
        val m = map ?: return@LaunchedEffect
        if (!hasLocationPermission || !m.locationComponent.isLocationComponentActivated) return@LaunchedEffect
        if (state.navigating) {
            m.locationComponent.cameraMode = CameraMode.TRACKING_GPS
            m.locationComponent.renderMode = RenderMode.GPS
            m.locationComponent.zoomWhileTracking(16.5)
            m.locationComponent.tiltWhileTracking(50.0)
        } else {
            m.locationComponent.cameraMode = CameraMode.NONE
            m.locationComponent.renderMode = RenderMode.COMPASS
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { _ ->
        Box(Modifier.fillMaxSize()) {
            MapLibreView(
                onMapReady = { m, s ->
                    map = m
                    style = s
                }
            )

            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                if (state.navigating) {
                    InstructionBanner(state.currentInstruction, state.distanceToTurnMeters, state.navStatus)
                } else {
                    SearchBar(
                        query = state.query,
                        results = state.searchResults,
                        onQueryChange = { viewModel.onQueryChange(it, searchBias()) },
                        onClear = viewModel::clearRoute,
                        onSelect = { viewModel.onPlaceSelected(it, currentLocation()) },
                    )
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButton(onClick = { showReportDialog = true }) {
                    Icon(Icons.Default.Warning, contentDescription = "Report")
                }
                state.route?.let { route ->
                    RouteCard(
                        route = route,
                        destination = state.destination,
                        navigating = state.navigating,
                        remainingKm = state.remainingKm,
                        remainingSeconds = state.remainingSeconds,
                        onStart = viewModel::startNavigation,
                        onCancel = viewModel::clearRoute,
                        onSimulate = if (BuildConfig.DEBUG) ({ showSimulateDialog = true }) else null,
                    )
                }
            }
        }
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
private fun MapLibreView(onMapReady: (MapLibreMap, Style) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).apply { onCreate(null) } }

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
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder().target(DEFAULT_CENTER).zoom(11.0).build()
            map.uiSettings.isCompassEnabled = true
            map.setStyle(Style.Builder().fromUri(BuildConfig.MAP_STYLE_URL)) { style ->
                style.addOverlayLayers()
                onMapReady(map, style)
            }
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
            PropertyFactory.lineColor("#1B4FBF"),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    addLayer(
        LineLayer("route-line", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#3D8BFF"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    addLayer(
        CircleLayer("destination", DEST_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor("#D20F39"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    addLayer(
        CircleLayer("matched-position", MATCHED_SOURCE).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor("#0B57D0"),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    results: List<Place>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (Place) -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, shadowElevation = 6.dp) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Where to?") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                    }
                },
            )
            LazyColumn {
                items(results) { place ->
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = { if (place.description.isNotBlank()) Text(place.description) },
                        leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        modifier = Modifier.clickable { onSelect(place) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionBanner(instruction: String?, distanceToTurnMeters: Double?, status: String?) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (distanceToTurnMeters != null) {
                Text("In ${formatKm(distanceToTurnMeters / 1000)}", style = MaterialTheme.typography.headlineSmall)
            }
            Text(instruction ?: "Follow the route", style = MaterialTheme.typography.titleMedium)
            if (status != null) {
                Text(status, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun RouteCard(
    route: Route,
    destination: Place?,
    navigating: Boolean,
    remainingKm: Double?,
    remainingSeconds: Double?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onSimulate: (() -> Unit)? = null,
) {
    val seconds = if (navigating) remainingSeconds ?: route.timeSeconds else route.timeSeconds
    val km = if (navigating) remainingKm ?: route.lengthKm else route.lengthKm
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(destination?.name ?: "Destination", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatDuration(seconds)} · ${formatKm(km)} · arrive ${formatArrival(context, seconds)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (!navigating && onSimulate != null) TextButton(onClick = onSimulate) { Text("Simulate") }
                OutlinedButton(onClick = onCancel) { Text(if (navigating) "Stop" else "Cancel") }
                if (!navigating) Button(onClick = onStart) { Text("Start") }
            }
        }
    }
}

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

private fun formatKm(km: Double): String =
    if (km < 1) "${(km * 1000).roundToInt()} m" else "%.1f km".format(km)

private fun formatDuration(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt()
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}

/** Arrival clock time in the phone's own 12/24-hour style. */
private fun formatArrival(context: Context, secondsFromNow: Double): String =
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
