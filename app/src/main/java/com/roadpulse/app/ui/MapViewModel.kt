package com.roadpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadpulse.app.BuildConfig
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.GeocodingClient
import com.roadpulse.app.data.InMemoryReportRepository
import com.roadpulse.app.data.Place
import com.roadpulse.app.data.Report
import com.roadpulse.app.data.ReportRepository
import com.roadpulse.app.data.ReportType
import com.roadpulse.app.data.Route
import com.roadpulse.app.data.RoutingClient
import com.roadpulse.app.nav.DriveReplay
import com.roadpulse.app.nav.NavEvent
import com.roadpulse.app.nav.NavigationService
import com.roadpulse.app.nav.NavigationSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val query: String = "",
    val searchResults: List<Place> = emptyList(),
    val destination: Place? = null,
    val route: Route? = null,
    val busy: Boolean = false,
    val navigating: Boolean = false,
    val currentInstruction: String? = null,
    val distanceToTurnMeters: Double? = null,
    val remainingKm: Double? = null,
    val remainingSeconds: Double? = null,
    val matchedPosition: GeoPoint? = null,
    /** Short status shown under the instruction while navigating (rerouting, GPS lost, offline). */
    val navStatus: String? = null,
    val reports: List<Report> = emptyList(),
    val message: String? = null,
)

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val geocoder = GeocodingClient(BuildConfig.GEOCODING_URL)
    private val router = RoutingClient(BuildConfig.ROUTING_URL)
    private val reportRepository: ReportRepository = InMemoryReportRepository()

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            reportRepository.reports.collect { reports -> _state.update { it.copy(reports = reports) } }
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
                            navStatus = when {
                                nav.rerouting -> "Finding a new route…"
                                nav.offline -> "Offline: keeping your current route"
                                nav.gpsLost -> "GPS signal lost: keep following the road"
                                nav.poorSignal -> "Weak GPS signal"
                                else -> null
                            },
                        )
                        ui.navigating -> MapUiState(reports = ui.reports, message = ui.message)
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
        _state.update { it.copy(query = place.name, searchResults = emptyList(), destination = place, route = null) }
        if (from == null) {
            _state.update { it.copy(message = "Waiting for your location. Enable GPS and try again.") }
            return
        }
        calculateRoute(from, place.location)
    }

    private fun calculateRoute(from: GeoPoint, to: GeoPoint) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { router.route(from, to) }
                .onSuccess { route -> _state.update { it.copy(route = route, busy = false) } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "Routing failed: ${e.message}") } }
        }
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
        _state.update { MapUiState(reports = it.reports, message = it.message) }
    }

    fun submitReport(type: ReportType, location: GeoPoint?) {
        if (location == null) {
            _state.update { it.copy(message = "Location unavailable, cannot place report") }
            return
        }
        viewModelScope.launch {
            reportRepository.submit(type, location)
            _state.update { it.copy(message = "${type.label} reported. Thanks!") }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
