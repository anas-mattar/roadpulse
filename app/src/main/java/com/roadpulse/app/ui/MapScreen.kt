package com.roadpulse.app.ui

import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.roadpulse.app.i18n.strings
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.font.FontWeight
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
import com.roadpulse.app.nav.start
import com.roadpulse.app.nav.DeviceLocation
import com.roadpulse.app.data.MapArea
import com.roadpulse.app.data.ServerSettings
import com.roadpulse.app.nav.DisplaySettings
import com.roadpulse.app.nav.MapColors
import com.roadpulse.app.nav.SunTimes
import com.roadpulse.app.nav.DriveReplay
import com.roadpulse.app.nav.LocationFix
import com.roadpulse.app.nav.NavigationSession
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
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlinx.coroutines.launch
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

private const val ROUTE_SOURCE = "route-source"

/** Field-test issue categories (code written to the drive log, label shown to the tester). */
private val FLAG_CATEGORIES = listOf(
    "wrong_prompt" to "Wrong or missing voice prompt",
    "prompt_timing" to "Prompt too early or too late",
    "false_reroute" to "Rerouted but I was on the route",
    "missed_reroute" to "Off route but no reroute",
    "wrong_position" to "Car shown in the wrong place",
    "bad_route" to "Route is wrong or illegal",
    "speed_limit" to "Speed limit wrong",
    "map_error" to "Map data error",
    "other" to "Other",
)
private const val ALT_ROUTE_SOURCE = "alt-route-source"
private const val TRAFFIC_SOURCE = "traffic-source"
private const val ROUTE_TRAFFIC_SOURCE = "route-traffic-source"
private const val TURN_SOURCE = "turn-source"
private const val ALT_ROUTE_LAYER = "alt-route-line"
private const val DEST_SOURCE = "destination-source"
private const val STOP_SOURCE = "stop-source"
private const val PARKING_SOURCE = "parking-source"
private const val REPORT_SOURCE = "report-source"
private const val REPORT_LAYER = "reports"
private const val REPORT_CLUSTER_LAYER = "report-clusters"
private const val REPORT_CLUSTER_COUNT_LAYER = "report-cluster-count"

/** Reports are grouped into a count bubble up to this zoom (city level); closer in, each shows. */
private const val CLUSTER_MAX_ZOOM = 13

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
    var showAbuseDialog by remember { mutableStateOf(false) }
    val voiceMuted by VoiceSettings.muted.collectAsStateWithLifecycle()
    val effectiveLanguage by VoiceSettings.effectiveLanguage.collectAsStateWithLifecycle()
    val narrator = narratorFor(effectiveLanguage)
    var showVoiceSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPlannedList by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showSimulateDialog by remember { mutableStateOf(false) }
    var showFlagDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    val shareLink by com.roadpulse.app.nav.ShareSession.link.collectAsStateWithLifecycle()
    val shareViewers by com.roadpulse.app.nav.ShareSession.viewers.collectAsStateWithLifecycle()
    var showSharePreview by remember { mutableStateOf(false) }
    var showSharedPanel by remember { mutableStateOf(false) }
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
    val s = strings
    val musicOn by com.roadpulse.app.nav.NowPlaying.enabled.collectAsStateWithLifecycle()
    val track by com.roadpulse.app.nav.NowPlaying.track.collectAsStateWithLifecycle()

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
    // While navigating, the puck shows the route-matched position instead (see below), so raw
    // fixes only update deviceFix then: one arrow, on the road.
    var deviceFix by remember { mutableStateOf<GeoPoint?>(null) }
    val navigatingNow by rememberUpdatedState(state.navigating)
    DisposableEffect(hasLocationPermission) {
        val source = DeviceLocation(context)
        if (hasLocationPermission) {
            source.start { location ->
                if (DriveReplay.active.value) return@start // simulated drive owns the position
                deviceFix = GeoPoint(location.latitude, location.longitude)
                if (!navigatingNow) map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(location)
            }
        }
        onDispose { source.stop() }
    }

    // Debug: during a simulated drive, show the simulated position as the location puck.
    LaunchedEffect(map) {
        DriveReplay.fixes.collect { fix ->
            deviceFix = fix.position
            if (!navigatingNow) map?.locationComponent?.takeIf { it.isLocationComponentActivated }
                ?.forceLocationUpdate(fix.toAndroidLocation())
        }
    }

    fun currentLocation(): GeoPoint? = deviceFix ?: map?.lastLocation(hasLocationPermission)

    // Search near the user; before the first GPS fix, near the area shown on the map.
    fun searchBias(): GeoPoint? = currentLocation()
        ?: map?.cameraPosition?.target?.let { GeoPoint(it.latitude, it.longitude) }

    // Voice search with the phone's own speech recognizer (in the guidance language).
    val voiceSearchAvailable = remember { android.speech.SpeechRecognizer.isRecognitionAvailable(context) }
    val voiceSearch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.let { spoken -> viewModel.onQueryChange(spoken, searchBias()) }
    }

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
    }
    // Traffic colours on the route line itself: the drive's stretches, or the selected choice's in the preview.
    val routePieces = if (state.navigating) state.trafficPieces else state.routeTraffic.getOrNull(state.selectedRoute)?.pieces.orEmpty()
    LaunchedEffect(style, state.route, routePieces) {
        val shape = state.route?.shape.orEmpty()
        style?.getSourceAs<GeoJsonSource>(ROUTE_TRAFFIC_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                com.roadpulse.app.nav.RouteTraffic.polylines(shape, routePieces).map { (level, pts) ->
                    Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) }))
                        .apply { addStringProperty("level", level) }
                }
            )
        )
    }
    // The next turn while driving: a marker where it is and the road name after it.
    LaunchedEffect(style, state.navigating, state.upcomingPoint, state.upcomingStreet) {
        val p = state.upcomingPoint
        style?.getSourceAs<GeoJsonSource>(TURN_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                if (!state.navigating || p == null) emptyList()
                else listOf(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply { addStringProperty("street", state.upcomingStreet.orEmpty()) })
            )
        )
    }
    // Other route choices in grey (preview only); tapping one selects it.
    LaunchedEffect(style, state.routeChoices, state.selectedRoute, state.navigating) {
        val alternatives = if (state.navigating) emptyList() else
            state.routeChoices.mapIndexedNotNull { i, r -> if (i == state.selectedRoute) null else i to r }
        style?.getSourceAs<GeoJsonSource>(ALT_ROUTE_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                alternatives.filter { it.second.shape.size >= 2 }.map { (i, r) ->
                    Feature.fromGeometry(LineString.fromLngLats(r.shape.map { Point.fromLngLat(it.lon, it.lat) }))
                        .apply { addNumberProperty("index", i) }
                }
            )
        )
    }
    // Fit all route choices on screen when they arrive (not on every selection change).
    LaunchedEffect(map, state.routeChoices) {
        val m = map ?: return@LaunchedEffect
        val points = state.routeChoices.flatMap { it.shape }
        if (points.size >= 2 && !state.navigating) {
            val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80, 220, 80, 900))
        }
    }
    DisposableEffect(map) {
        val m = map
        val listener = MapLibreMap.OnMapClickListener { point ->
            val screen = m?.projection?.toScreenLocation(point) ?: return@OnMapClickListener false
            val area = android.graphics.RectF(screen.x - 30, screen.y - 30, screen.x + 30, screen.y + 30)
            // A count bubble zooms in far enough to split it.
            val cluster = m.queryRenderedFeatures(area, REPORT_CLUSTER_LAYER).firstOrNull()
            if (cluster != null) {
                val source = m.style?.getSourceAs<GeoJsonSource>(REPORT_SOURCE)
                val zoom = source?.getClusterExpansionZoom(cluster)?.toDouble() ?: (m.cameraPosition.zoom + 2)
                val p = cluster.geometry() as? Point
                val target = p?.let { LatLng(it.latitude(), it.longitude()) } ?: point
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom.coerceAtLeast(CLUSTER_MAX_ZOOM + 1.0)))
                return@OnMapClickListener true
            }
            // A report icon wins over a route line underneath it.
            val report = m.queryRenderedFeatures(area, REPORT_LAYER).firstOrNull()?.getStringProperty("id")
            if (report != null) {
                viewModel.selectReport(report)
                return@OnMapClickListener true
            }
            val hit = m.queryRenderedFeatures(area, ALT_ROUTE_LAYER).firstOrNull()
            val index = hit?.getNumberProperty("index")?.toInt()
            if (index == null) {
                viewModel.selectReport(null) // tap on empty map closes the report card
                return@OnMapClickListener false
            }
            viewModel.selectRoute(index)
            true
        }
        m?.addOnMapClickListener(listener)
        onDispose { m?.removeOnMapClickListener(listener) }
    }
    LaunchedEffect(style, state.route, state.stops) {
        val points = state.route?.stops?.takeIf { it.isNotEmpty() } ?: state.stops.map { it.location }
        style?.getSourceAs<GeoJsonSource>(STOP_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })
        )
    }
    LaunchedEffect(style, state.saved.parking) {
        val p = state.saved.parking?.location
        style?.getSourceAs<GeoJsonSource>(PARKING_SOURCE)?.setGeoJson(
            if (p == null) FeatureCollection.fromFeatures(emptyList())
            else FeatureCollection.fromFeature(Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)))
        )
    }
    // Long press anywhere on the map: actions for that point (drive, stop, save, park).
    DisposableEffect(map) {
        val m = map
        val listener = MapLibreMap.OnMapLongClickListener { point ->
            viewModel.onMapLongPress(GeoPoint(point.latitude, point.longitude))
            true
        }
        m?.addOnMapLongClickListener(listener)
        onDispose { m?.removeOnMapLongClickListener(listener) }
    }
    // Compass and north-up preferences.
    val northUp by DisplaySettings.northUp.collectAsStateWithLifecycle()
    val showCompass by DisplaySettings.showCompass.collectAsStateWithLifecycle()
    val flatDriving by DisplaySettings.flatDriving.collectAsStateWithLifecycle()
    LaunchedEffect(map, northUp, showCompass, state.navigating) {
        val m = map ?: return@LaunchedEffect
        m.uiSettings.isRotateGesturesEnabled = !northUp
        m.uiSettings.isCompassEnabled = showCompass
        // Top left, below the turn card while driving (the buttons on the right stay free).
        val px = context.resources.displayMetrics.density
        m.uiSettings.compassGravity = android.view.Gravity.TOP or android.view.Gravity.START
        m.uiSettings.setCompassMargins((20 * px).toInt(), ((if (state.navigating) 230 else 90) * px).toInt(), 0, 0)
        if (northUp && m.cameraPosition.bearing != 0.0) {
            m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(m.cameraPosition).bearing(0.0).build()))
        }
    }
    LaunchedEffect(style, state.destination) {
        style?.getSourceAs<GeoJsonSource>(DEST_SOURCE)?.setGeoJson(state.destination.toFeatureCollection())
    }
    // While navigating, the location arrow shows the route-matched position (on the route line
    // when matched, the raw fix when off route), pointing in the direction of travel.
    var lastPuck by remember { mutableStateOf<GeoPoint?>(null) }
    var lastBearing by remember { mutableStateOf(0f) }
    LaunchedEffect(state.navigating, state.matchedPosition) {
        val p = state.matchedPosition
        if (!state.navigating || p == null) {
            lastPuck = null
            return@LaunchedEffect
        }
        val prev = lastPuck
        if (prev != null && com.roadpulse.app.data.Geo.distanceMeters(prev, p) >= 1.5) {
            lastBearing = com.roadpulse.app.nav.RouteMatcher.bearing(prev, p).toFloat()
        }
        lastPuck = p
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(
            android.location.Location("route").also { l ->
                l.latitude = p.lat
                l.longitude = p.lon
                l.bearing = lastBearing
                l.accuracy = 5f
                l.time = System.currentTimeMillis()
                l.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
            }
        )
    }
    // Tell the reports source which area is on screen whenever the map comes to rest.
    DisposableEffect(map) {
        val m = map
        val listener = MapLibreMap.OnCameraIdleListener {
            m?.projection?.visibleRegion?.latLngBounds?.let { b ->
                viewModel.onMapAreaChanged(MapArea(b.longitudeWest, b.latitudeSouth, b.longitudeEast, b.latitudeNorth))
            }
        }
        m?.addOnCameraIdleListener(listener)
        onDispose { m?.removeOnCameraIdleListener(listener) }
    }
    val reportMarkers = rememberReportMarkers()
    LaunchedEffect(style, reportMarkers) {
        val s = style ?: return@LaunchedEffect
        reportMarkers.forEach { (type, bitmap) -> s.addImage(markerImageName(type), bitmap) }
    }
    LaunchedEffect(style, state.reports, state.selectedReportId) {
        style?.getSourceAs<GeoJsonSource>(REPORT_SOURCE)?.setGeoJson(state.reports.toFeatureCollection(state.selectedReportId))
    }

    // Navigation mode: follow the car with a tilted camera. Re-applied after every map style load
    // (a style switch resets it) and whenever the driver taps Re-center after panning the map.
    var following by remember { mutableStateOf(true) }
    var trackingListenerAdded by remember { mutableStateOf(false) }
    LaunchedEffect(state.navigating) { if (state.navigating) following = true }
    LaunchedEffect(state.navigating, map, style, hasLocationPermission, following, northUp, flatDriving) {
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
            lc.cameraMode = if (northUp) CameraMode.TRACKING_GPS_NORTH else CameraMode.TRACKING_GPS
            lc.zoomWhileTracking(16.5)
            lc.tiltWhileTracking(if (flatDriving) 0.0 else 50.0)
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
    // Known before the first style loads, so the app does not open with the day map at night
    // and switch a moment later.
    var isNight by remember {
        mutableStateOf(SunTimes.isNight(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude, System.currentTimeMillis()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            val p = currentLocation() ?: GeoPoint(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude)
            isNight = SunTimes.isNight(p.lat, p.lon, System.currentTimeMillis())
            delay(60_000)
        }
    }
    val darkMap = mapColors == MapColors.DARK || (mapColors == MapColors.AUTO && isNight)
    val localMaps by ServerSettings.useLocalMaps.collectAsStateWithLifecycle()
    val styleUrl = remember(darkMap, localMaps) { ServerSettings.mapStyleUrl(darkMap) }

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
                    searchFrom = state.searchFrom,
                    saved = state.saved,
                    settingShortcut = state.settingShortcut,
                    onShortcut = { viewModel.onShortcut(it, currentLocation()) },
                    onChangeShortcut = viewModel::changeShortcut,
                    onRecent = { viewModel.onPlaceSelected(it, currentLocation()) },
                    onQueryChange = { viewModel.onQueryChange(it, searchBias()) },
                    onClear = viewModel::clearRoute,
                    onSelect = { viewModel.onPlaceSelected(it, currentLocation()) },
                    onOpenSettings = { showMenu = true },
                    onRecenter = {
                        currentLocation()?.let { p ->
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 15.0))
                        }
                    },
                    onReport = { showReportDialog = true },
                    onFavourite = { viewModel.onPlaceSelected(it, currentLocation()) },
                    onRemoveFavourite = viewModel::removeFavourite,
                    onShowParking = {
                        state.saved.parking?.location?.let { p ->
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lon), 17.0))
                        }
                    },
                    onClearParking = viewModel::clearParking,
                    onCategory = if (state.placesAvailable) ({ c -> viewModel.searchCategory(c, searchBias()) }) else null,
                    categoryTitle = state.categoryTitle,
                    onCloseCategory = viewModel::clearCategory,
                    onPlanned = { viewModel.openPlanned(it.id, currentLocation()) },
                    onRemovePlanned = viewModel::removePlanned,
                    onOpenProfile = null,
                    onVoiceSearch = if (voiceSearchAvailable) ({
                        voiceSearch.launch(
                            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, when (effectiveLanguage) {
                                    GuidanceLanguage.MS -> "ms-MY"
                                    // Place names in Malaysia are Malay/English; Arabic speech would not match them.
                                    GuidanceLanguage.AR -> "en-US"
                                    GuidanceLanguage.EN -> "en-US"
                                })
                                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Where to?")
                        )
                    }) else null,
                )

                ScreenMode.PREVIEW -> PreviewOverlay(
                    destination = state.destination,
                    route = state.route,
                    choices = state.routeChoices,
                    tolls = state.tollEstimates,
                    selected = state.selectedRoute,
                    onSelectRoute = viewModel::selectRoute,
                    busy = state.busy,
                    onBack = viewModel::clearRoute,
                    onGo = viewModel::startNavigation,
                    onSimulate = if (BuildConfig.DEBUG) ({ showSimulateDialog = true }) else null,
                    stops = state.stops,
                    onRemoveStop = viewModel::removeStop,
                    optionsSummary = state.routeOptions.summary(strings),
                    parkingNearby = state.parkingNearby,
                    onChooseParking = viewModel::chooseParking,
                    onLeaveLater = if (state.route != null && state.stops.isEmpty()) ({ showPlanDialog = true }) else null,
                    traffic = state.routeTraffic,
                    onGoRoute = { i ->
                        viewModel.selectRoute(i)
                        viewModel.startNavigation()
                    },
                )

                ScreenMode.DRIVING -> DrivingOverlay(
                    state = state,
                    narrator = narrator,
                    muted = voiceMuted,
                    onToggleMute = { VoiceSettings.setMuted(!voiceMuted) },
                    onReport = { showReportDialog = true },
                    onStop = viewModel::clearRoute,
                    onLoadRoutes = viewModel::loadAlternatives,
                    onPickRoute = viewModel::switchRoute,
                    onCloseRoutes = viewModel::closeAlternatives,
                    following = following,
                    onRecenter = { following = true },
                    track = if (musicOn) track else null,
                    onPlayPause = com.roadpulse.app.nav.NowPlaying::playPause,
                    onNextTrack = com.roadpulse.app.nav.NowPlaying::next,
                    onPreviousTrack = com.roadpulse.app.nav.NowPlaying::previous,
                    onShare = if (state.placesAvailable) ({ showSharePreview = true }) else null,
                    sharing = shareLink != null,
                    shareViewers = shareViewers,
                    onStopSharing = { showSharedPanel = true },
                    onTakeFasterRoute = com.roadpulse.app.nav.NavigationSession::acceptFasterRoute,
                    onKeepRoute = com.roadpulse.app.nav.NavigationSession::declineFasterRoute,
                    onOverview = {
                        following = false
                        val m = map
                        val r = state.route
                        if (m != null && r != null) {
                            m.locationComponent.takeIf { it.isLocationComponentActivated }?.cameraMode = CameraMode.NONE
                            val rest = com.roadpulse.app.nav.RouteGeometry.remaining(r.shape, state.matchedPosition)
                            if (rest.size >= 2) {
                                val bounds = LatLngBounds.Builder().includes(rest.map { LatLng(it.lat, it.lon) }).build()
                                // Room for the turn card at the top and the trip card at the bottom.
                                m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120, 520, 120, 560), 800)
                            }
                        }
                    },
                    onFlag = if (BuildConfig.DEBUG) ({ showFlagDialog = true }) else null,
                    showSpeedometer = DisplaySettings.showSpeedometer.collectAsStateWithLifecycle().value,
                    limitDisplay = DisplaySettings.limitDisplay.collectAsStateWithLifecycle().value,
                    speedingMarginKmh = DisplaySettings.speedingMarginKmh.collectAsStateWithLifecycle().value,
                )
            }

            state.pinned?.let { pin ->
                if (!showReportDialog && state.stillThere == null) {
                    PinnedPlaceCard(
                        place = pin,
                        canAddStop = state.destination != null,
                        onDrive = { viewModel.driveToPinned(currentLocation()) },
                        onAddStop = { viewModel.addPinnedAsStop(currentLocation()) },
                        onSave = { name -> viewModel.saveFavourite(pin, name) },
                        onPark = { viewModel.parkHere(pin.location) },
                        onClose = viewModel::dismissPinned,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                        onAddPlace = if (state.placesAvailable) ({ name, kind, note -> viewModel.addCommunityPlace(pin.location, name, kind, note) }) else null,
                    )
                }
            }

            state.stillThere?.let { report ->
                StillThereCard(
                    report = report,
                    onAnswer = viewModel::answerStillThere,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }

            if (showReportDialog) {
                ReportPicker(
                    onPick = { type, subtype, minutes ->
                        showReportDialog = false
                        viewModel.submitReport(type, currentLocation(), subtype, minutes)
                    },
                    onDismiss = { showReportDialog = false },
                    autoCloseMillis = if (mode == ScreenMode.DRIVING) 10_000L else null,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }

            val selected = state.reports.firstOrNull { it.id == state.selectedReportId }
            if (selected != null) {
                val now by produceState(System.currentTimeMillis(), selected.id) {
                    while (true) {
                        kotlinx.coroutines.delay(30_000)
                        value = System.currentTimeMillis()
                    }
                }
                ReportCard(
                    report = selected,
                    nowMillis = now,
                    own = state.selectedReportOwn,
                    busy = state.reportBusy,
                    onStillThere = { viewModel.voteOnSelected(true) },
                    onNotThere = { viewModel.voteOnSelected(false) },
                    onFlag = { showAbuseDialog = true },
                    onClose = { viewModel.selectReport(null) },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }
        }
    }

    if (showAbuseDialog) {
        AlertDialog(
            onDismissRequest = { showAbuseDialog = false },
            title = { Text(strings.whatsWrongWithReport) },
            text = {
                Column {
                    com.roadpulse.app.data.FlagReason.entries.forEach { reason ->
                        TextButton(
                            onClick = {
                                showAbuseDialog = false
                                viewModel.flagSelected(reason)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(strings.flagReason(reason)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAbuseDialog = false }) { Text(strings.cancel) } },
        )
    }

    if (showProfile) {
        ProfileDialog(state.profile, state.profileError, onSaveNickname = viewModel::saveNickname, onDismiss = { showProfile = false })
    }

    // Live traffic lines in view.
    LaunchedEffect(style, state.trafficLines) {
        val features = state.trafficLines.map { line ->
            Feature.fromGeometry(
                org.maplibre.geojson.LineString.fromLngLats(line.points.map { org.maplibre.geojson.Point.fromLngLat(it[1], it[0]) })
            ).apply { addStringProperty("level", line.level) }
        }
        style?.getSourceAs<GeoJsonSource>(TRAFFIC_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Share my drive: what the link shows, then the share sheet with the message.
    fun sendShareLink(url: String) {
        val eta = formatArrivalText(context, (state.remainingSeconds ?: 0.0) + state.trafficDelaySeconds)
        val text = s.shareText(state.destination?.name.orEmpty(), eta, url)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, text)
        context.startActivity(android.content.Intent.createChooser(send, s.shareDrive))
    }
    if (showSharePreview) {
        val eta = formatArrivalText(context, (state.remainingSeconds ?: 0.0) + state.trafficDelaySeconds)
        AlertDialog(
            onDismissRequest = { showSharePreview = false },
            title = { Text(s.shareYourDrive) },
            text = {
                Column {
                    Text(s.shareTo(state.destination?.name.orEmpty()), fontWeight = FontWeight.Medium)
                    Text(eta, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
                    Text(s.shareExplain, fontSize = 14.sp, color = RP.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSharePreview = false
                    uiScope.launch {
                        runCatching { com.roadpulse.app.nav.ShareSession.start(context, state.profile?.nickname) }
                            .onSuccess { link -> sendShareLink(link.url) }
                            .onFailure { viewModel.showMessage(s.shareFailed) }
                    }
                }) { Text(s.shareDrive) }
            },
            dismissButton = { TextButton(onClick = { showSharePreview = false }) { Text(s.cancel) } },
        )
    }
    if (showSharedPanel) {
        val link = shareLink
        if (link == null) showSharedPanel = false
        else AlertDialog(
            onDismissRequest = { showSharedPanel = false },
            title = { Text(s.sharedDrive) },
            text = {
                Column {
                    Text(s.viewers(shareViewers), fontWeight = FontWeight.Medium)
                    Text(s.shareTo(state.destination?.name.orEmpty()), fontSize = 14.sp, color = RP.TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSharedPanel = false; com.roadpulse.app.nav.ShareSession.stop() }) {
                    Text(s.stopSharing, color = RP.Danger)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showSharedPanel = false; sendShareLink(link.url) }) { Text(s.shareDrive) }
                    TextButton(onClick = { showSharedPanel = false }) { Text(s.close) }
                }
            },
        )
    }

    if (showPlanDialog) {
        val route = state.route
        if (route == null) showPlanDialog = false
        else PlanDriveDialog(
            driveSeconds = route.timeSeconds,
            onDismiss = { showPlanDialog = false },
            onPlan = { arriveBy -> if (viewModel.planDrive(arriveBy)) showPlanDialog = false },
        )
    }

    // A "time to leave" reminder was tapped: open that drive's route once a location is known.
    val openPlanned by com.roadpulse.app.nav.LeaveReminders.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openPlanned) {
        val id = openPlanned ?: return@LaunchedEffect
        var from = currentLocation()
        var tries = 0
        while (from == null && tries++ < 10) {
            kotlinx.coroutines.delay(500)
            from = currentLocation()
        }
        com.roadpulse.app.nav.LeaveReminders.openRequest.value = null
        viewModel.openPlanned(id, from)
    }

    if (showMenu) {
        MenuPanel(
            profile = state.profile,
            onProfile = if (state.placesAvailable) ({ showMenu = false; viewModel.loadProfile(); showProfile = true }) else null,
            onPlanned = { showMenu = false; showPlannedList = true },
            onSettings = { showMenu = false; showVoiceSettings = true },
            onHelp = { showMenu = false; showHelp = true },
            onDismiss = { showMenu = false },
        )
    }
    if (showPlannedList) {
        PlannedDrivesDialog(
            planned = state.saved.planned,
            onOpen = { drive -> showPlannedList = false; viewModel.onPlaceSelected(drive.destination, currentLocation()) },
            onRemove = viewModel::removePlanned,
            onDismiss = { showPlannedList = false },
        )
    }
    if (showHelp) HelpDialog(version = BuildConfig.VERSION_NAME, onDismiss = { showHelp = false })

    if (showVoiceSettings) {
        VoiceSettingsDialog(
            onDismiss = { showVoiceSettings = false },
            onClearPlaces = {
                showVoiceSettings = false
                viewModel.clearSavedPlaces()
            },
            onExportData = {
                viewModel.exportMyData { json ->
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/json")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, "Jalanna data")
                        .putExtra(android.content.Intent.EXTRA_TEXT, json)
                    context.startActivity(android.content.Intent.createChooser(send, s.exportData))
                }
            },
            onDeleteData = {
                showVoiceSettings = false
                viewModel.deleteMyData()
            },
        )
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

    if (showFlagDialog) {
        AlertDialog(
            onDismissRequest = { showFlagDialog = false },
            title = { Text("Flag an issue (field test)") },
            text = {
                Column {
                    FLAG_CATEGORIES.forEach { (code, label) ->
                        TextButton(
                            onClick = {
                                showFlagDialog = false
                                NavigationSession.flag(code)
                                viewModel.showMessage("Flag saved: $label")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFlagDialog = false }) { Text("Cancel") } },
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
            // An exception here would reach the map's native code and close the app: log it instead.
            runCatching { style.addOverlayLayers() }
                .onFailure { android.util.Log.e("RoadPulse", "Could not add map layers", it) }
            onStyleLoaded(style)
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

private fun Style.addOverlayLayers() {
    // If the location puck's layers are already in this style (they are re-added on a style
    // switch, sometimes before ours), insert our layers just below them so the puck stays on top.
    val locationLayer = layers.firstOrNull { l ->
        (l.id.startsWith("mapbox-location") || l.id.startsWith("maplibre-location")) && l.id.endsWith("layer")
    }?.id
    fun add(layer: org.maplibre.android.style.layers.Layer) {
        if (locationLayer != null) addLayerBelow(layer, locationLayer) else addLayer(layer)
    }
    addSource(GeoJsonSource(TRAFFIC_SOURCE))
    addSource(GeoJsonSource(ALT_ROUTE_SOURCE))
    addSource(GeoJsonSource(ROUTE_SOURCE))
    addSource(GeoJsonSource(ROUTE_TRAFFIC_SOURCE))
    addSource(GeoJsonSource(TURN_SOURCE))
    addSource(GeoJsonSource(DEST_SOURCE))
    addSource(GeoJsonSource(STOP_SOURCE))
    addSource(GeoJsonSource(PARKING_SOURCE))
    addSource(
        GeoJsonSource(
            REPORT_SOURCE,
            org.maplibre.android.style.sources.GeoJsonOptions()
                .withCluster(true).withClusterMaxZoom(CLUSTER_MAX_ZOOM).withClusterRadius(44),
        )
    )

    // Live traffic under the routes: orange = slow, red = jammed (free-flowing roads are not drawn).
    add(
        LineLayer("traffic-line", TRAFFIC_SOURCE).withProperties(
            PropertyFactory.lineColor(
                org.maplibre.android.style.expressions.Expression.match(
                    org.maplibre.android.style.expressions.Expression.get("level"),
                    org.maplibre.android.style.expressions.Expression.literal("JAM"),
                    org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#D20F39")),
                    org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#F59F00")),
                )
            ),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    add(
        LineLayer(ALT_ROUTE_LAYER, ALT_ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#9E9C94"),
            PropertyFactory.lineWidth(7f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    add(
        LineLayer("route-casing", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#0A5A5C"),
            PropertyFactory.lineWidth(10f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    add(
        LineLayer("route-line", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#3FC1B0"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    // Slow (amber) and jammed (red) stretches drawn on top of the route line.
    add(
        LineLayer("route-traffic", ROUTE_TRAFFIC_SOURCE).withProperties(
            PropertyFactory.lineColor(
                org.maplibre.android.style.expressions.Expression.match(
                    org.maplibre.android.style.expressions.Expression.get("level"),
                    org.maplibre.android.style.expressions.Expression.literal("JAM"),
                    org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#D20F39")),
                    org.maplibre.android.style.expressions.Expression.color(android.graphics.Color.parseColor("#F59F00")),
                )
            ),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    // Next turn: a white dot on the route and the road name in a teal label above it.
    add(
        CircleLayer("turn-point", TURN_SOURCE).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeColor("#0A5A5C"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    add(
        SymbolLayer("turn-street", TURN_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("street")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#0A5A5C"),
            PropertyFactory.textHaloWidth(4f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
            PropertyFactory.textOffset(arrayOf(0f, -1.1f)),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
    )
    add(
        CircleLayer("destination", DEST_SOURCE).withProperties(
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleColor("#08343A"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    // Stops on the way: smaller teal dots.
    add(
        CircleLayer("stops", STOP_SOURCE).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#0B7F78"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    // The parked car: a teal dot with a "P".
    add(
        CircleLayer("parking", PARKING_SOURCE).withProperties(
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleColor("#0F6E56"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
    add(
        SymbolLayer("parking-label", PARKING_SOURCE).withProperties(
            PropertyFactory.textField("P"),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        )
    )
    // Report icons ("report-POLICE" etc., registered by rememberReportMarkers). Reports on the
    // same spot are fanned out with per-feature offsets (ReportLayout); newest drawn on top.
    add(
        SymbolLayer(REPORT_LAYER, REPORT_SOURCE).withProperties(
            PropertyFactory.iconImage(Expression.concat(Expression.literal("report-"), Expression.get("type"))),
            PropertyFactory.iconOffset(Expression.get("offset")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSortKey(Expression.get("order")),
            PropertyFactory.iconSize(Expression.switchCase(Expression.get("selected"), Expression.literal(1.25f), Expression.literal(1f))),
        ).withFilter(Expression.not(Expression.has("point_count")))
    )
    // Zoomed out: nearby reports become one bubble with their count. Tap it to zoom in.
    add(
        CircleLayer(REPORT_CLUSTER_LAYER, REPORT_SOURCE).withProperties(
            PropertyFactory.circleColor("#0B7F78"),
            PropertyFactory.circleRadius(
                Expression.step(Expression.get("point_count"), Expression.literal(16f),
                    Expression.stop(5, 19f), Expression.stop(20, 23f))
            ),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ).withFilter(Expression.has("point_count"))
    )
    add(
        SymbolLayer(REPORT_CLUSTER_COUNT_LAYER, REPORT_SOURCE).withProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count_abbreviated"))),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")), // in both the day and night styles
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ).withFilter(Expression.has("point_count"))
    )
}

@SuppressLint("MissingPermission")
private fun MapLibreMap.enableLocation(context: Context, style: Style) {
    val component = locationComponent
    if (!component.isLocationComponentActivated) {
        component.activateLocationComponent(
            // No layerAbove/layerBelow: after a style switch the location layers can be added back
            // before ours, and pointing at one of ours would then crash. addOverlayLayers puts our
            // layers below the location layers instead, so your position always stays on top.
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(false)
                // While driving (RenderMode.GPS) the car is our orange arrow, as on iPhone.
                .locationComponentOptions(
                    LocationComponentOptions.builder(context).gpsDrawable(com.roadpulse.app.R.drawable.ic_nav_arrow).build()
                )
                .build()
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

private fun List<Report>.toFeatureCollection(selectedId: String?): FeatureCollection {
    val offsets = com.roadpulse.app.data.ReportLayout.offsets(this)
    return FeatureCollection.fromFeatures(
        sortedBy { it.createdAtMillis }.mapIndexed { order, report ->
            Feature.fromGeometry(Point.fromLngLat(report.location.lon, report.location.lat)).apply {
                addStringProperty("id", report.id)
                addStringProperty("type", report.type.name)
                addNumberProperty("order", if (report.id == selectedId) Int.MAX_VALUE else order)
                addBooleanProperty("selected", report.id == selectedId)
                val (dx, dy) = offsets[report.id] ?: (0f to 0f)
                addProperty("offset", com.google.gson.JsonArray().apply { add(dx); add(dy) })
            }
        }
    )
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun formatKm(km: Double): String = strings.distance(km)

@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun formatDuration(seconds: Double): String = strings.duration((seconds / 60).roundToInt())

/** Arrival clock time for messages outside composition. */
internal fun formatArrivalText(context: Context, secondsFromNow: Double): String = formatArrival(context, secondsFromNow)

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
private fun VoiceSettingsDialog(onDismiss: () -> Unit, onClearPlaces: () -> Unit, onExportData: () -> Unit = {}, onDeleteData: () -> Unit = {}) {
    var showPolicy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (showPolicy) PrivacyPolicyDialog(onDismiss = { showPolicy = false })
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(strings.deleteData) },
            text = { Text(strings.deleteDataConfirm) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteData() }) { Text(strings.delete, color = RP.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(strings.cancel) } },
        )
    }
    val context = LocalContext.current
    val language by VoiceSettings.language.collectAsStateWithLifecycle()
    val missing by VoiceSettings.missingVoices.collectAsStateWithLifecycle()
    val muted by VoiceSettings.muted.collectAsStateWithLifecycle()
    val mapColors by DisplaySettings.mapColors.collectAsStateWithLifecycle()
    // Grouped like other navigation apps: a list of sections, each opening its own page.
    var page by remember { mutableStateOf<String?>(null) }
    val pages = buildList {
        add(Triple("general", strings.general, Icons.Filled.Settings))
        add(Triple("map", strings.mapDisplay, Icons.Filled.Map))
        add(Triple("voice", strings.voiceAndSound, Icons.AutoMirrored.Filled.VolumeUp))
        add(Triple("navigation", strings.navigationSettings, Icons.Filled.Route))
        add(Triple("vehicle", strings.vehicleDetails, Icons.Filled.DirectionsCar))
        add(Triple("alerts", strings.alertsAndReports, Icons.Filled.ReportProblem))
        add(Triple("speed", strings.speedometer, Icons.Filled.Speed))
        add(Triple("privacy", strings.privacy, Icons.Filled.Lock))
        if (ServerSettings.localAvailable) add(Triple("server", "Server (debug builds)", Icons.Filled.Dns))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != null) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back,
                        modifier = Modifier.padding(end = 12.dp).clickable { page = null })
                }
                Text(pages.firstOrNull { it.first == page }?.second ?: strings.settings)
            }
        },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                when (page) {
                    null -> pages.forEach { (id, label, icon) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { page = id }.padding(vertical = 12.dp),
                        ) {
                            Icon(icon, contentDescription = null, tint = RP.Brand, modifier = Modifier.size(22.dp))
                            Text(label, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = RP.TextSecondary)
                        }
                    }
                    "general" -> {
                        Text(strings.language, style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary)
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
                                strings.noVoiceInstalled(language.label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    "map" -> {
                        Text(strings.mapColours, style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary)
                        MapColors.entries.forEach { option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { DisplaySettings.setMapColors(option) },
                            ) {
                                RadioButton(selected = mapColors == option, onClick = null)
                                Text(strings.mapColors(option), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        MapDisplaySettings()
                    }
                    "voice" -> {
                        SettingSwitch(strings.muteVoiceSetting, muted, VoiceSettings::setMuted)
                        MusicSettings()
                    }
                    "navigation" -> NavigationSettings()
                    "vehicle" -> VehicleSettings()
                    "alerts" -> TrafficSettings()
                    "speed" -> SpeedometerSettings()
                    "server" -> ServerDebugSettings()
                    "privacy" -> {
                        Text(strings.privacyText, style = MaterialTheme.typography.bodySmall, color = RP.TextSecondary)
                        val noPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        TextButton(onClick = { showPolicy = true }, contentPadding = noPadding) { Text(strings.privacyPolicy) }
                        TextButton(onClick = onExportData, contentPadding = noPadding) { Text(strings.exportData) }
                        TextButton(onClick = onClearPlaces, contentPadding = noPadding) {
                            Text(strings.clearSavedPlaces, color = RP.Danger)
                        }
                        TextButton(onClick = { confirmDelete = true }, contentPadding = noPadding) { Text(strings.deleteData, color = RP.Danger) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.done) } },
    )
}

/** Debug builds: use the RoadPulse servers on this laptop. */
@Composable
private fun ServerDebugSettings() {
    val useLocal by ServerSettings.useLocal.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Use my laptop's server", modifier = Modifier.weight(1f))
        Switch(checked = useLocal, onCheckedChange = ServerSettings::setUseLocal)
    }
    Text(
        if (useLocal) "Routing and live reports from Docker on this computer."
        else "Public routing; reports stay on this phone.",
        style = MaterialTheme.typography.bodySmall,
        color = RP.TextSecondary,
    )
    if (useLocal) {
        val link by ServerSettings.link.collectAsStateWithLifecycle()
        Text("Laptop reached from", fontSize = 13.sp, color = RP.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        ServerSettings.LaptopLink.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { ServerSettings.setLink(option) },
            ) {
                RadioButton(selected = link == option, onClick = null)
                Text(option.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (ServerSettings.localMapsAvailable) {
        val useLocalMaps by ServerSettings.useLocalMaps.collectAsStateWithLifecycle()
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Maps from my laptop", modifier = Modifier.weight(1f))
            Switch(checked = useLocalMaps, onCheckedChange = ServerSettings::setUseLocalMaps)
        }
        Text(
            if (useLocalMaps) "Map tiles from this computer (works without internet). Needs setup-offline-maps.ps1."
            else "Map tiles from OpenFreeMap (internet).",
            style = MaterialTheme.typography.bodySmall,
            color = RP.TextSecondary,
        )
    }
}

/** Opt-in to send anonymous speed samples for live traffic (ADR 0004). Off by default. */
@Composable
private fun TrafficSettings() {
    val on by com.roadpulse.app.nav.TrafficUploader.enabled.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    SettingsHeader(strings.helpTraffic)
    // Turning it on asks for consent first, with the full explanation; turning it off is immediate.
    SettingSwitch(strings.helpTraffic, on) { want -> if (want) asking = true else com.roadpulse.app.nav.TrafficUploader.setEnabled(false) }
    Text(strings.helpTrafficText, style = MaterialTheme.typography.bodySmall, color = RP.TextSecondary)
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(strings.trafficConsentTitle) },
            text = { Text(strings.helpTrafficText) },
            confirmButton = { TextButton(onClick = { asking = false; com.roadpulse.app.nav.TrafficUploader.setEnabled(true) }) { Text(strings.allow) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(strings.notNow) } },
        )
    }
}

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.privacyPolicy) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                strings.privacyPolicyText.forEach { Text(it, fontSize = 14.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.done) } },
    )
}

/** Music controls while driving: needs notification access, granted in Android settings. */
@Composable
private fun MusicSettings() {
    val context = LocalContext.current
    val on by com.roadpulse.app.nav.NowPlaying.enabled.collectAsStateWithLifecycle()
    val hasAccess = com.roadpulse.app.nav.NowPlaying.hasAccess(context)
    SettingSwitch(strings.musicControls, on && hasAccess) { want ->
        com.roadpulse.app.nav.NowPlaying.setEnabled(context, want)
        if (want && !com.roadpulse.app.nav.NowPlaying.hasAccess(context)) {
            runCatching {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    Text(strings.musicControlsHelp, style = MaterialTheme.typography.bodySmall, color = RP.TextSecondary)
    if (on && !hasAccess) {
        Text(strings.musicNeedsAccess, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * Plan the previewed drive: arrive-by time (today or tomorrow) and the leave time worked out from
 * the route's travel time plus a spare margin.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlanDriveDialog(driveSeconds: Double, onDismiss: () -> Unit, onPlan: (Long) -> Unit) {
    val context = LocalContext.current
    val now = remember { java.time.LocalDateTime.now() }
    val suggested = remember { now.plusHours(1).withMinute(0) }
    val picker = androidx.compose.material3.rememberTimePickerState(
        initialHour = suggested.hour, initialMinute = suggested.minute,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    var tomorrow by remember { mutableStateOf(false) }
    val arriveBy = now.toLocalDate().plusDays(if (tomorrow) 1 else 0).atTime(picker.hour, picker.minute)
    val arriveMillis = arriveBy.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val plan = com.roadpulse.app.data.PlannedDrive(0, com.roadpulse.app.data.Place("", "", com.roadpulse.app.data.GeoPoint(0.0, 0.0)), arriveMillis, driveSeconds)
    val leaveAt = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(plan.leaveAtMillis))
    val late = plan.leaveAtMillis < System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.planDrive) },
        text = {
            Column {
                Text(strings.arriveBy, style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    androidx.compose.material3.FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text(strings.today) })
                    androidx.compose.material3.FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text(strings.tomorrow) })
                }
                androidx.compose.material3.TimeInput(state = picker)
                Text(
                    if (late) strings.tooLate else strings.leaveAt(leaveAt, plan.driveMinutes, com.roadpulse.app.data.PlannedDrive.SPARE_MINUTES),
                    color = if (late) MaterialTheme.colorScheme.error else RP.Brand,
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = { TextButton(enabled = !late, onClick = { onPlan(arriveMillis) }) { Text(strings.setReminder) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

/** Roads to avoid. */
@Composable
private fun NavigationSettings() {
    val options by com.roadpulse.app.data.RouteSettings.options.collectAsStateWithLifecycle()
    SettingSwitch(strings.avoidTolls, options.avoidTolls) { on -> com.roadpulse.app.data.RouteSettings.update { it.copy(avoidTolls = on) } }
    SettingSwitch(strings.avoidHighways, options.avoidHighways) { on -> com.roadpulse.app.data.RouteSettings.update { it.copy(avoidHighways = on) } }
    SettingSwitch(strings.avoidFerries, options.avoidFerries) { on -> com.roadpulse.app.data.RouteSettings.update { it.copy(avoidFerries = on) } }
    SettingSwitch(strings.allowUnpaved, options.allowUnpaved) { on -> com.roadpulse.app.data.RouteSettings.update { it.copy(allowUnpaved = on) } }
}

/** Car, motorcycle or taxi (routes and toll prices). */
@Composable
private fun VehicleSettings() {
    val options by com.roadpulse.app.data.RouteSettings.options.collectAsStateWithLifecycle()
    com.roadpulse.app.data.Vehicle.entries.forEach { v ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { com.roadpulse.app.data.RouteSettings.update { it.copy(vehicle = v) } },
        ) {
            RadioButton(selected = options.vehicle == v, onClick = null)
            Text(strings.vehicle(v), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Speedometer, speed limit sign and the speeding warning. */
@Composable
private fun SpeedometerSettings() {
    val showSpeedometer by DisplaySettings.showSpeedometer.collectAsStateWithLifecycle()
    val limitDisplay by DisplaySettings.limitDisplay.collectAsStateWithLifecycle()
    val margin by DisplaySettings.speedingMarginKmh.collectAsStateWithLifecycle()
    val sound by DisplaySettings.speedingSound.collectAsStateWithLifecycle()
    SettingSwitch(strings.showSpeedometer, showSpeedometer) { DisplaySettings.set(DisplaySettings.showSpeedometer, "show_speedometer", it) }
    Text(strings.showSpeedLimit, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    com.roadpulse.app.nav.LimitDisplay.entries.forEach { option ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { DisplaySettings.set(DisplaySettings.limitDisplay, "limit_display", option) },
        ) {
            RadioButton(selected = limitDisplay == option, onClick = null)
            Text(strings.limitDisplay(option), modifier = Modifier.padding(start = 8.dp))
        }
    }
    Text(strings.warnOverLimitBy, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(0, 3, 5, 10).forEach { kmh ->
            androidx.compose.material3.FilterChip(
                selected = margin == kmh,
                onClick = { DisplaySettings.set(DisplaySettings.speedingMarginKmh, "speeding_margin", kmh) },
                label = { Text(if (kmh == 0) "0" else "+$kmh") },
            )
        }
    }
    SettingSwitch(strings.soundWhenSpeeding, sound) { DisplaySettings.set(DisplaySettings.speedingSound, "speeding_sound", it) }
}

/** North up, compass, flat or tilted driving map. */
@Composable
private fun MapDisplaySettings() {
    val northUp by DisplaySettings.northUp.collectAsStateWithLifecycle()
    val compass by DisplaySettings.showCompass.collectAsStateWithLifecycle()
    val flat by DisplaySettings.flatDriving.collectAsStateWithLifecycle()
    SettingsHeader(strings.mapView)
    SettingSwitch(strings.northUp, northUp) { DisplaySettings.set(DisplaySettings.northUp, "north_up", it) }
    SettingSwitch(strings.showCompass, compass) { DisplaySettings.set(DisplaySettings.showCompass, "show_compass", it) }
    SettingSwitch(strings.flatMap, flat) { DisplaySettings.set(DisplaySettings.flatDriving, "flat_driving", it) }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = RP.TextSecondary, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

