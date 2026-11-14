package com.roadpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadpulse.app.BuildConfig
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.GeocodingClient
import com.roadpulse.app.data.BackendReportRepository
import com.roadpulse.app.data.InMemoryReportRepository
import com.roadpulse.app.data.MapArea
import com.roadpulse.app.data.ReportRejectedException
import com.roadpulse.app.data.ServerSettings
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.PlacesStore
import com.roadpulse.app.data.SavedPlaces
import com.roadpulse.app.data.Shortcut
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportRepository
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.DriveReplay
import com.roadpulse.app.nav.NavEvent
import com.roadpulse.app.nav.NavigationService
import com.roadpulse.app.nav.NavigationSession
import com.roadpulse.app.nav.VoiceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.File

data class MapUiState(
    val query: String = "",
    val searchResults: List<Place> = emptyList(),
    val destination: Place? = null,
    /** The selected route (what Go now will navigate). */
    val route: Route? = null,
    /** All route choices for the preview; [route] is routeChoices[selectedRoute]. */
    val routeChoices: List<Route> = emptyList(),
    val selectedRoute: Int = 0,
    val busy: Boolean = false,
    val navigating: Boolean = false,
    val currentInstruction: String? = null,
    val distanceToTurnMeters: Double? = null,
    val remainingKm: Double? = null,
    val remainingSeconds: Double? = null,
    val matchedPosition: GeoPoint? = null,
    val turnType: Int? = null,
    val thenType: Int? = null,
    val thenGapMeters: Double? = null,
    val speedKmh: Int? = null,
    val speedLimitKph: Int? = null,
    /** Short status shown under the instruction while navigating (rerouting, GPS lost, offline). */
    val navStatus: String? = null,
    val reports: List<Report> = emptyList(),
    /** Home, Work and recent destinations (stored on the device only). */
    val saved: SavedPlaces = SavedPlaces(),
    /** Set while the user is choosing the address for Home or Work. */
    val settingShortcut: Shortcut? = null,
    val message: String? = null,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val geocoder = GeocodingClient(BuildConfig.GEOCODING_URL)
    private val router get() = RoutingClient(ServerSettings.routingUrl())
    private var reportRepository: ReportRepository = InMemoryReportRepository()
    private var reportsJob: Job? = null
    private var lastVisibleArea: MapArea? = null
    private val placesStore = PlacesStore(File(application.filesDir, "places.json"))

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        _state.update { it.copy(saved = placesStore.load()) }
        // Reports come from the server when one is configured, otherwise they stay on this phone.
        viewModelScope.launch {
            ServerSettings.useLocal.collect { useReportsSource() }
        }
        // Mirror the active drive (owned by NavigationSession, advanced by NavigationService).
        viewModelScope.launch {
            NavigationSession.state.collect { nav ->
                _state.update { ui ->
                    when {
                        nav != null -> ui.copy(
                            navigating = true,
                            route = nav.route,
                            destination = nav.destination,
                            busy = nav.rerouting,
                            currentInstruction = nav.instruction,
                            distanceToTurnMeters = nav.distanceToTurnMeters,
                            remainingKm = nav.remainingKm,
                            remainingSeconds = nav.remainingSeconds,
                            matchedPosition = nav.displayPosition,
                            turnType = nav.upcomingType,
                            thenType = nav.thenType,
                            thenGapMeters = nav.thenGapMeters,
                            speedKmh = nav.speedMps?.let { (it * 3.6).roundToInt() },
                            speedLimitKph = nav.speedLimitKph,
                            navStatus = VoiceSettings.narrator().let { n ->
                                when {
                                    nav.rerouting -> n.statusFindingRoute
                                    nav.offline -> n.statusOffline
                                    nav.gpsLost -> n.statusGpsLost
                                    nav.poorSignal -> n.statusWeakGps
                                    else -> null
                                }
                            },
                        )
                        ui.navigating -> MapUiState(reports = ui.reports, message = ui.message, saved = ui.saved)
                        else -> ui
                    }
                }
            }
        }
        // Continue a drive that was in progress when Android stopped the app.
        if (NavigationSession.state.value == null && NavigationSession.restoreIfSaved()) {
            val name = NavigationSession.state.value?.destination?.name
            runCatching { NavigationService.start(getApplication()) }
                .onSuccess { _state.update { it.copy(message = "Resumed your drive to $name") } }
                .onFailure { NavigationSession.stop() }
        }
        viewModelScope.launch {
            NavigationSession.events.collect { event ->
                if (event is NavEvent.Arrived) _state.update { it.copy(message = "You have arrived") }
            }
        }
    }

    fun onQueryChange(query: String, near: GeoPoint?) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.length < 3) {
            _state.update { it.copy(searchResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce typing
            try {
                val results = geocoder.search(query, near)
                _state.update { it.copy(searchResults = results) }
            } catch (e: CancellationException) {
                throw e // superseded by newer typing: not an error
            } catch (e: Exception) {
                _state.update { it.copy(message = "Search failed: ${e.message}") }
            }
        }
    }

    fun onPlaceSelected(place: Place, from: GeoPoint?) {
        val shortcut = _state.value.settingShortcut
        if (shortcut != null) {
            val saved = placesStore.setShortcut(shortcut, place)
            val label = if (shortcut == Shortcut.HOME) "Home" else "Work"
            _state.update {
                it.copy(saved = saved, settingShortcut = null, query = "", searchResults = emptyList(), message = "$label saved")
            }
            return
        }
        val saved = placesStore.addRecent(place)
        _state.update { it.copy(saved = saved, query = place.name, searchResults = emptyList(), destination = place, route = null) }
        if (from == null) {
            _state.update { it.copy(message = "Waiting for your location. Enable GPS and try again.") }
            return
        }
        calculateRoute(from, place.location)
    }

    private fun calculateRoute(from: GeoPoint, to: GeoPoint) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { router.routes(from, to) }
                .onSuccess { routes ->
                    _state.update { it.copy(route = routes.first(), routeChoices = routes, selectedRoute = 0, busy = false) }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "Routing failed: ${e.message}") } }
        }
    }

    /** Home/Work tapped: route there, or ask for the address if it is not set yet. */
    fun onShortcut(shortcut: Shortcut, from: GeoPoint?) {
        val place = when (shortcut) {
            Shortcut.HOME -> _state.value.saved.home
            Shortcut.WORK -> _state.value.saved.work
        }
        if (place == null) changeShortcut(shortcut) else onPlaceSelected(place, from)
    }

    /** Starts choosing a (new) address for Home or Work. */
    fun changeShortcut(shortcut: Shortcut) {
        _state.update { it.copy(settingShortcut = shortcut, query = "", searchResults = emptyList()) }
    }

    fun clearSavedPlaces() {
        val cleared = placesStore.clear()
        _state.update { it.copy(saved = cleared, message = "Saved places cleared") }
    }

    /** Picks one of the route choices in the preview. */
    fun selectRoute(index: Int) {
        val choices = _state.value.routeChoices
        if (_state.value.navigating || index !in choices.indices) return
        _state.update { it.copy(route = choices[index], selectedRoute = index) }
    }

    fun startNavigation() {
        val route = _state.value.route ?: return
        val destination = _state.value.destination ?: return
        NavigationSession.start(route, destination)
        runCatching { NavigationService.start(getApplication()) }
            .onFailure { e ->
                NavigationSession.stop()
                _state.update { it.copy(message = "Could not start navigation: ${e.message}") }
            }
    }

    /** Debug builds: navigate the current route with a simulated drive. */
    fun simulateDrive(config: DriveReplay.Config) {
        if (!BuildConfig.DEBUG) return
        if (_state.value.route == null || _state.value.destination == null) return
        NavigationSession.start(_state.value.route!!, _state.value.destination!!, persistDrive = false)
        DriveReplay.start(config) // before the service starts, so it uses simulated fixes
        runCatching { NavigationService.start(getApplication()) }
            .onFailure { e ->
                DriveReplay.stop()
                NavigationSession.stop()
                _state.update { it.copy(message = "Could not start simulation: ${e.message}") }
            }
    }

    fun clearRoute() {
        DriveReplay.stop()
        NavigationSession.stop()
        _state.update { MapUiState(reports = it.reports, message = it.message, saved = it.saved) }
    }

    private fun useReportsSource() {
        reportsJob?.cancel()
        reportRepository.close()
        val url = ServerSettings.backendUrl()
        reportRepository = if (url.isEmpty()) {
            InMemoryReportRepository()
        } else {
            BackendReportRepository(url, ServerSettings.tokenStore(getApplication(), url), viewModelScope) {
                if (BuildConfig.DEBUG) android.util.Log.i("RoadPulseLive", it)
            }
        }
        val repo = reportRepository
        reportsJob = viewModelScope.launch {
            repo.reports.collect { reports -> _state.update { it.copy(reports = reports) } }
        }
        lastVisibleArea?.let(repo::watchArea)
    }

    /** The map stopped moving: load and watch reports for this area. */
    fun onMapAreaChanged(visible: MapArea) {
        lastVisibleArea = visible
        reportRepository.watchArea(visible)
    }

    fun submitReport(type: ReportType, location: GeoPoint?) {
        if (location == null) {
            _state.update { it.copy(message = "Location unavailable, cannot place report") }
            return
        }
        viewModelScope.launch {
            try {
                reportRepository.submit(type, location)
                _state.update { it.copy(message = "${type.label} reported. Thanks!") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReportRejectedException) {
                _state.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Could not send report. Check your connection and try again.") }
            }
        }
    }

    override fun onCleared() {
        reportRepository.close()
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
