package com.roadpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadpulse.app.BuildConfig
import com.roadpulse.app.data.GeoPoint
import com.roadpulse.app.data.GeocodingClient
import com.roadpulse.app.data.BackendReportRepository
import com.roadpulse.app.data.FlagReason
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
    /** Nearest report on the route ahead while driving. */
    val reportAhead: com.roadpulse.app.nav.ReportAhead? = null,
    /** Short status shown under the instruction while navigating (rerouting, GPS lost, offline). */
    val navStatus: String? = null,
    val reports: List<Report> = emptyList(),
    /** Report the driver tapped (its card is shown while it is still on the map). */
    val selectedReportId: String? = null,
    val selectedReportOwn: Boolean = false,
    /** A vote or flag is being sent. */
    val reportBusy: Boolean = false,
    /** Home, Work and recent destinations (stored on the device only). */
    val saved: SavedPlaces = SavedPlaces(),
    /** Set while the user is choosing the address for Home or Work. */
    val settingShortcut: Shortcut? = null,
    val message: String? = null,
    /** Stops on the way to [destination], in order (preview and driving). */
    val stops: List<Place> = emptyList(),
    /** A point the driver long-pressed on the map, with its name once known. */
    val pinned: Place? = null,
    /** Asked right after driving past a report: is it still there? */
    val stillThere: Report? = null,
    /** Category results are shown (e.g. "Fuel"): the header above the results. */
    val categoryTitle: String? = null,
    /** The RoadPulse backend is configured, so places (fuel, parking…) can be searched. */
    val placesAvailable: Boolean = false,
    /** Car parks near the destination (preview), nearest first. */
    val parkingNearby: List<Place> = emptyList(),
    /** Nearest fixed speed camera ahead while driving. */
    val cameraAhead: com.roadpulse.app.nav.CameraAhead? = null,
    /** Route options in effect (shown in the preview). */
    val routeOptions: com.roadpulse.app.data.RouteOptions = com.roadpulse.app.data.RouteOptions(),
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
                            reportAhead = nav.reportAhead,
                            cameraAhead = nav.cameraAhead,
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
                        ui.navigating -> MapUiState(reports = ui.reports, message = ui.message, saved = ui.saved, selectedReportId = ui.selectedReportId, selectedReportOwn = ui.selectedReportOwn, routeOptions = ui.routeOptions, placesAvailable = ui.placesAvailable)
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
                when (event) {
                    is NavEvent.Arrived -> _state.update { it.copy(message = "You have arrived") }
                    is NavEvent.PassedReport -> askStillThere(event.report)
                    else -> Unit
                }
            }
        }
        // Re-plan the preview when the driver changes vehicle or roads to avoid.
        viewModelScope.launch {
            com.roadpulse.app.data.RouteSettings.options.collect { options ->
                val ui = _state.value
                val changed = ui.routeOptions != options
                _state.update { it.copy(routeOptions = options) }
                if (changed && !ui.navigating && ui.destination != null && ui.route != null) {
                    lastOrigin?.let { calculateRoute(it, ui.destination.location) }
                }
            }
        }
    }

    fun onQueryChange(query: String, near: GeoPoint?) {
        _state.update { it.copy(query = query, categoryTitle = null) }
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

    /** Lists places of [category] nearest to [near] (the user's position or the map centre). */
    fun searchCategory(category: com.roadpulse.app.data.PlaceCategory, near: GeoPoint?) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty() || near == null) return
        searchJob?.cancel()
        _state.update { it.copy(query = "", categoryTitle = "${category.label} nearby", searchResults = emptyList()) }
        searchJob = viewModelScope.launch {
            try {
                val found = com.roadpulse.app.data.PlacesClient(url).nearby(category, near, radiusM = 10_000)
                _state.update {
                    it.copy(
                        searchResults = found.map { p -> p.toPlace() },
                        categoryTitle = if (found.isEmpty()) "No ${category.label.lowercase()} within 10 km" else it.categoryTitle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(categoryTitle = null, message = "Could not load places. Check your connection.") }
            }
        }
    }

    fun clearCategory() = _state.update { it.copy(categoryTitle = null, searchResults = emptyList()) }

    /** Use a suggested car park as the destination instead (keeps the stops). */
    fun chooseParking(parking: Place) {
        val dest = _state.value.destination ?: return
        val named = parking.copy(name = "Parking · ${parking.name}", description = "Near ${dest.name}")
        _state.update { it.copy(destination = named, parkingNearby = emptyList(), route = null) }
        lastOrigin?.let { calculateRoute(it, named.location, lookForParking = false) }
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
        _state.update {
            it.copy(saved = saved, query = place.name, searchResults = emptyList(), destination = place, route = null,
                stops = emptyList(), categoryTitle = null, parkingNearby = emptyList())
        }
        if (from == null) {
            _state.update { it.copy(message = "Waiting for your location. Enable GPS and try again.") }
            return
        }
        calculateRoute(from, place.location)
    }

    /** Suggests car parks within 600 m of the destination (shown in the preview). */
    private fun findParkingNear(to: GeoPoint) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty() || _state.value.destination?.name?.startsWith("Parking") == true) return
        viewModelScope.launch {
            val found = runCatching { com.roadpulse.app.data.PlacesClient(url).nearby(com.roadpulse.app.data.PlaceCategory.PARKING, to, radiusM = 600, limit = 5) }
                .getOrNull().orEmpty()
            // Not worth suggesting when the destination itself is (practically) a car park.
            val useful = found.filter { it.distanceM > 40 }
            _state.update { if (it.destination?.location == to) it.copy(parkingNearby = useful.map { p -> p.toPlace() }) else it }
        }
    }

    /** Where the current preview was planned from (to re-plan after a stop or option change). */
    private var lastOrigin: GeoPoint? = null

    private fun calculateRoute(from: GeoPoint, to: GeoPoint, lookForParking: Boolean = true) {
        lastOrigin = from
        if (lookForParking) findParkingNear(to)
        val stops = _state.value.stops.map { it.location }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { router.routes(from, to, stops = stops, options = com.roadpulse.app.data.RouteSettings.options.value) }
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
        _state.update { MapUiState(reports = it.reports, message = it.message, saved = it.saved, selectedReportId = it.selectedReportId, selectedReportOwn = it.selectedReportOwn, routeOptions = it.routeOptions, placesAvailable = it.placesAvailable) }
    }

    private fun useReportsSource() {
        reportsJob?.cancel()
        reportRepository.close()
        val url = ServerSettings.backendUrl()
        _state.update { it.copy(placesAvailable = url.isNotEmpty()) }
        reportRepository = if (url.isEmpty()) {
            InMemoryReportRepository()
        } else {
            BackendReportRepository(url, ServerSettings.tokenStore(getApplication(), url), viewModelScope) {
                if (BuildConfig.DEBUG) android.util.Log.i("RoadPulseLive", it)
            }
        }
        val repo = reportRepository
        reportsJob = viewModelScope.launch {
            repo.reports.collect { reports ->
                com.roadpulse.app.data.ReportFeed.reports.value = reports
                _state.update { ui ->
                    // Close the card when its report leaves the map (expired, removed, voted away).
                    val stillShown = ui.selectedReportId?.let { id -> reports.any { it.id == id } } ?: false
                    ui.copy(reports = reports, selectedReportId = if (stillShown) ui.selectedReportId else null)
                }
            }
        }
        lastVisibleArea?.let(repo::watchArea)
    }

    fun selectReport(id: String?) {
        _state.update { it.copy(selectedReportId = id, selectedReportOwn = id != null && reportRepository.isOwn(id)) }
    }

    /** "Still there" / "Not there" on the selected report. */
    fun voteOnSelected(stillThere: Boolean) {
        val id = _state.value.selectedReportId ?: return
        askedOrVoted += id
        reportAction(if (stillThere) "Thanks! Confirmed for other drivers." else "Thanks! Other drivers will be told it's gone.") {
            reportRepository.vote(id, stillThere)
        }
    }

    fun flagSelected(reason: FlagReason) {
        val id = _state.value.selectedReportId ?: return
        reportAction("Thanks. Our moderators will review it.", closeCard = true) { reportRepository.flag(id, reason) }
    }

    private fun reportAction(thanks: String, closeCard: Boolean = true, action: suspend () -> Unit) {
        if (_state.value.reportBusy) return
        _state.update { it.copy(reportBusy = true) }
        viewModelScope.launch {
            val message = try {
                action()
                thanks
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReportRejectedException) {
                e.message
            } catch (e: Exception) {
                "Could not send. Check your connection and try again."
            }
            _state.update { it.copy(reportBusy = false, message = message, selectedReportId = if (closeCard) null else it.selectedReportId) }
        }
    }

    /** The map stopped moving: load and watch reports for this area. */
    fun onMapAreaChanged(visible: MapArea) {
        lastVisibleArea = visible
        reportRepository.watchArea(visible)
    }

    // --- long press, stops, favourites, parking ------------------------------------------------

    /** The driver long-pressed the map: show actions for that point and look up its name. */
    fun onMapLongPress(point: GeoPoint) {
        val pin = Place("Dropped pin", "%.5f, %.5f".format(java.util.Locale.ROOT, point.lat, point.lon), point)
        _state.update { it.copy(pinned = pin) }
        viewModelScope.launch {
            val named = runCatching { geocoder.reverse(point) }.getOrNull() ?: return@launch
            _state.update { if (it.pinned?.location == point) it.copy(pinned = named) else it }
        }
    }

    fun dismissPinned() = _state.update { it.copy(pinned = null) }

    /** Drive to the pinned point (replaces the current destination and stops). */
    fun driveToPinned(from: GeoPoint?) {
        val pin = _state.value.pinned ?: return
        if (_state.value.navigating) clearRoute()
        _state.update { it.copy(pinned = null, stops = emptyList()) }
        onPlaceSelected(pin, from)
    }

    /** Adds the pinned point as a stop: re-plans the preview, or the active drive. */
    fun addPinnedAsStop(from: GeoPoint?) {
        val pin = _state.value.pinned ?: return
        _state.update { it.copy(pinned = null) }
        addStop(pin, from)
    }

    fun addStop(place: Place, from: GeoPoint?) {
        val ui = _state.value
        if (from == null) {
            _state.update { it.copy(message = "Waiting for your location. Enable GPS and try again.") }
            return
        }
        when {
            ui.navigating -> {
                NavigationSession.addStop(place.location, from)
                _state.update { it.copy(stops = it.stops + place, message = "Stop added: ${place.name}") }
            }
            ui.destination != null -> {
                _state.update { it.copy(stops = it.stops + place) }
                calculateRoute(from, ui.destination.location)
            }
            else -> _state.update { it.copy(message = "Choose a destination first, then add stops") }
        }
    }

    fun removeStop(place: Place) {
        val ui = _state.value
        if (ui.navigating) return
        _state.update { it.copy(stops = it.stops - place) }
        val from = lastOrigin ?: return
        ui.destination?.let { calculateRoute(from, it.location) }
    }

    fun saveFavourite(place: Place, name: String) {
        if (name.isBlank()) return
        val saved = placesStore.addFavourite(name, place)
        _state.update { it.copy(saved = saved, pinned = null, message = "Saved: ${name.trim()}") }
    }

    fun removeFavourite(place: Place) {
        _state.update { it.copy(saved = placesStore.removeFavourite(place), message = "Removed: ${place.name}") }
    }

    /** Marks where the car is parked (the pinned point, or the current position). */
    fun parkHere(point: GeoPoint?) {
        val p = point ?: run {
            _state.update { it.copy(message = "Location unavailable") }
            return
        }
        val saved = placesStore.setParking(com.roadpulse.app.data.ParkedCar(p, System.currentTimeMillis()))
        _state.update { it.copy(saved = saved, pinned = null, message = "Parking spot saved") }
    }

    fun clearParking() = _state.update { it.copy(saved = placesStore.setParking(null)) }

    // --- "still there?" after driving past a report ---------------------------------------------

    private val askedOrVoted = HashSet<String>()
    private var stillThereJob: Job? = null

    private fun askStillThere(report: Report) {
        if (report.id in askedOrVoted || reportRepository.isOwn(report.id)) return
        askedOrVoted += report.id
        _state.update { it.copy(stillThere = report) }
        stillThereJob?.cancel()
        stillThereJob = viewModelScope.launch {
            delay(STILL_THERE_SHOWN_MS)
            _state.update { if (it.stillThere?.id == report.id) it.copy(stillThere = null) else it }
        }
    }

    fun answerStillThere(stillThere: Boolean?) {
        val report = _state.value.stillThere ?: return
        _state.update { it.copy(stillThere = null) }
        stillThereJob?.cancel()
        if (stillThere == null) return // "Skip"
        viewModelScope.launch {
            runCatching { reportRepository.vote(report.id, stillThere) }
                .onSuccess { _state.update { it.copy(message = "Thanks!") } }
        }
    }

    fun submitReport(type: ReportType, location: GeoPoint?, subtype: String? = null, durationMinutes: Int? = null) {
        if (location == null) {
            _state.update { it.copy(message = "Location unavailable, cannot place report") }
            return
        }
        viewModelScope.launch {
            try {
                reportRepository.submit(type, location, subtype, durationMinutes)
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

    private companion object {
        /** The "still there?" card closes by itself after this long. */
        const val STILL_THERE_SHOWN_MS = 12_000L
    }
}
