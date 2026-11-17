package com.roadpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadpulse.app.BuildConfig
import com.roadpulse.app.data.toPlace
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
import kotlinx.coroutines.flow.drop
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
    /** Toll estimate per route choice (same order); null while loading or when there is no server. */
    val tollEstimates: List<com.roadpulse.app.data.TollEstimate?> = emptyList(),
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
    /** Lanes before the next turn, left to right. */
    val lanes: List<com.roadpulse.app.data.Lane> = emptyList(),
    /** Nearest school on the route ahead while driving. */
    val schoolAhead: com.roadpulse.app.nav.SchoolAhead? = null,
    /** Slow and jammed roads in view (live traffic, privacy threshold applied by the server). */
    val trafficLines: List<com.roadpulse.app.data.TrafficLine> = emptyList(),
    /** This device's points and level; null until loaded (or without a server). */
    val profile: com.roadpulse.app.data.Profile? = null,
    val profileError: Boolean = false,
    /** Extra minutes from live traffic on the route ahead, and a faster route on offer. */
    val trafficDelaySeconds: Double = 0.0,
    val fasterRoute: com.roadpulse.app.nav.FasterRoute? = null,
    /** Route options in effect (shown in the preview). */
    val routeOptions: com.roadpulse.app.data.RouteOptions = com.roadpulse.app.data.RouteOptions(),
    /** Live traffic on each route choice (same order as [routeChoices]); empty while loading or without a server. */
    val routeTraffic: List<com.roadpulse.app.nav.RouteTrafficInfo> = emptyList(),
    /** Slow and jammed stretches of the route being driven. */
    val trafficPieces: List<com.roadpulse.app.nav.TrafficPiece> = emptyList(),
    /** "Routes" during a drive: other ways from here (null when the list is closed). */
    val driveAlternatives: List<Route>? = null,
    val loadingAlternatives: Boolean = false,
    /** Where the last search was made from (distance on each result). */
    val searchFrom: GeoPoint? = null,
    /** The next turn while driving (map marker and road name). */
    val upcomingPoint: GeoPoint? = null,
    val upcomingStreet: String? = null,
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
        // Live traffic in view, refreshed every minute.
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                refreshTraffic()
            }
        }
        _state.update { it.copy(saved = placesStore.load()) }
        // Reports come from the server when one is configured, otherwise they stay on this phone.
        viewModelScope.launch {
            ServerSettings.useLocal.collect { useReportsSource() }
        }
        // The server found through the address books arrived or changed: switch to it.
        viewModelScope.launch {
            ServerSettings.found.drop(1).collect { useReportsSource() }
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
                            lanes = nav.lanes,
                            trafficDelaySeconds = nav.trafficDelaySeconds,
                            fasterRoute = nav.fasterRoute,
                            trafficPieces = nav.trafficPieces,
                            upcomingPoint = nav.upcomingPoint,
                            upcomingStreet = nav.upcomingStreet,
                            schoolAhead = nav.schoolAhead,
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
                        ui.navigating -> MapUiState(reports = ui.reports, message = ui.message, saved = ui.saved, selectedReportId = ui.selectedReportId, selectedReportOwn = ui.selectedReportOwn, routeOptions = ui.routeOptions, placesAvailable = ui.placesAvailable, trafficLines = ui.trafficLines, profile = ui.profile)
                        else -> ui
                    }
                }
            }
        }
        // Continue a drive that was in progress when Android stopped the app.
        if (NavigationSession.state.value == null && NavigationSession.restoreIfSaved()) {
            val name = NavigationSession.state.value?.destination?.name
            runCatching { NavigationService.start(getApplication()) }
                .onSuccess { _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().resumedDrive(name.orEmpty())) } }
                .onFailure { NavigationSession.stop() }
        }
        viewModelScope.launch {
            NavigationSession.events.collect { event ->
                when (event) {
                    is NavEvent.Arrived -> _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().arrived) }
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
                val url = ServerSettings.backendUrl()
                val community = url.takeIf { it.isNotEmpty() }?.let {
                    com.roadpulse.app.data.CommunityPlacesClient(com.roadpulse.app.data.DeviceApi(it, ServerSettings.tokenStore(getApplication(), it)))
                }
                val directory = url.takeIf { it.isNotEmpty() }?.let { com.roadpulse.app.data.DirectoryClient(it) }
                val results = com.roadpulse.app.data.PlaceSearch.search(geocoder, community, query, near, com.roadpulse.app.i18n.currentStrings(),
                    limit = 8, directory = directory)
                _state.update { it.copy(searchResults = results, searchFrom = near) }
            } catch (e: CancellationException) {
                throw e // superseded by newer typing: not an error
            } catch (e: Exception) {
                _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().searchFailed(e.message)) }
            }
        }
    }

    /** Lists places of [category] nearest to [near] (the user's position or the map centre). */
    fun searchCategory(category: com.roadpulse.app.data.PlaceCategory, near: GeoPoint?) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty() || near == null) return
        searchJob?.cancel()
        _state.update { it.copy(query = "", categoryTitle = com.roadpulse.app.i18n.currentStrings().categoryNearby(category), searchResults = emptyList()) }
        searchJob = viewModelScope.launch {
            try {
                val found = com.roadpulse.app.data.PlacesClient(url).nearby(category, near, radiusM = 10_000)
                _state.update {
                    it.copy(
                        searchResults = found.map { p -> p.toPlace(com.roadpulse.app.i18n.currentStrings()) },
                        categoryTitle = if (found.isEmpty()) com.roadpulse.app.i18n.currentStrings().noneWithin10Km(category) else it.categoryTitle,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(categoryTitle = null, message = com.roadpulse.app.i18n.currentStrings().couldNotLoadPlaces) }
            }
        }
    }

    fun clearCategory() = _state.update { it.copy(categoryTitle = null, searchResults = emptyList()) }

    /** Use a suggested car park as the destination instead (keeps the stops). */
    fun chooseParking(parking: Place) {
        val dest = _state.value.destination ?: return
        val named = parking.copy(name = com.roadpulse.app.i18n.currentStrings().parkingFor(parking.name), description = com.roadpulse.app.i18n.currentStrings().near(dest.name))
        _state.update { it.copy(destination = named, parkingNearby = emptyList(), route = null) }
        lastOrigin?.let { calculateRoute(it, named.location, lookForParking = false) }
    }

    fun onPlaceSelected(place: Place, from: GeoPoint?) {
        val shortcut = _state.value.settingShortcut
        if (shortcut != null) {
            val saved = placesStore.setShortcut(shortcut, place)
            val savedMessage = com.roadpulse.app.i18n.currentStrings().shortcutSaved(shortcut == Shortcut.HOME)
            _state.update {
                it.copy(saved = saved, settingShortcut = null, query = "", searchResults = emptyList(), message = savedMessage)
            }
            return
        }
        val saved = placesStore.addRecent(place)
        _state.update {
            it.copy(saved = saved, query = place.name, searchResults = emptyList(), destination = place, route = null,
                stops = emptyList(), categoryTitle = null, parkingNearby = emptyList())
        }
        if (from == null) {
            _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().waitingForLocation) }
            return
        }
        calculateRoute(from, place.location)
    }

    /** Suggests car parks within 600 m of the destination (shown in the preview). */
    private fun findParkingNear(to: GeoPoint) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty() || _state.value.destination?.name?.startsWith(com.roadpulse.app.i18n.currentStrings().parkingFor("")) == true) return
        viewModelScope.launch {
            val found = runCatching { com.roadpulse.app.data.PlacesClient(url).nearby(com.roadpulse.app.data.PlaceCategory.PARKING, to, radiusM = 600, limit = 5) }
                .getOrNull().orEmpty()
            // Not worth suggesting when the destination itself is (practically) a car park.
            val useful = found.filter { it.distanceM > 40 }
            _state.update { if (it.destination?.location == to) it.copy(parkingNearby = useful.map { p -> p.toPlace(com.roadpulse.app.i18n.currentStrings()) }) else it }
        }
    }

    /** Asks the backend what each toll route costs (from the owner's toll table); shown on the route cards. */
    private fun estimateTolls(routes: List<Route>) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty() || routes.none { it.hasToll }) return
        val vehicle = com.roadpulse.app.data.RouteSettings.options.value.vehicle
        viewModelScope.launch {
            val client = com.roadpulse.app.data.TollClient(url)
            val estimates = routes.map { r ->
                if (!r.hasToll) null else runCatching { client.estimate(r.shape, vehicle) }.getOrNull()
            }
            _state.update { if (it.routeChoices === routes) it.copy(tollEstimates = estimates) else it }
        }
    }

    /** Traffic bars for the route cards (one traffic request for all choices). */
    private fun loadRouteTraffic(routes: List<Route>) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty()) return
        viewModelScope.launch {
            val info = com.roadpulse.app.nav.RouteTraffic.forRoutes(url, routes)
            _state.update { if (it.routeChoices === routes) it.copy(routeTraffic = info) else it }
        }
    }

    /** "Add a missing place": saved on the server; the driver finds it in search at once, others after review. */
    fun addCommunityPlace(at: GeoPoint, name: String, kind: com.roadpulse.app.data.CommunityPlaceKind, note: String) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty()) return
        _state.update { it.copy(pinned = null) }
        viewModelScope.launch {
            val s = com.roadpulse.app.i18n.currentStrings()
            runCatching {
                com.roadpulse.app.data.CommunityPlacesClient(com.roadpulse.app.data.DeviceApi(url, ServerSettings.tokenStore(getApplication(), url)))
                    .add(name, kind, at, note)
            }.onSuccess { _state.update { it.copy(message = s.placeAdded) } }
                .onFailure { _state.update { it.copy(message = s.placeAddFailed) } }
        }
    }

    /** "Report a problem with this place": [at] is where the place really is, when the driver gives a position. */
    fun reportPlaceProblem(placeId: String, kind: com.roadpulse.app.data.PlaceProblemKind, at: GeoPoint?, note: String) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty()) return
        viewModelScope.launch {
            val s = com.roadpulse.app.i18n.currentStrings()
            runCatching {
                com.roadpulse.app.data.PlaceProblemsClient(com.roadpulse.app.data.DeviceApi(url, ServerSettings.tokenStore(getApplication(), url)))
                    .report(placeId, kind, at, note)
            }.onSuccess { _state.update { it.copy(message = s.placeProblemSent) } }
                .onFailure { _state.update { it.copy(message = s.placeProblemFailed) } }
        }
    }

    /** "Routes" during a drive: loads other ways from the car's position. */
    fun loadAlternatives() {
        if (!_state.value.navigating || _state.value.loadingAlternatives) return
        _state.update { it.copy(loadingAlternatives = true, driveAlternatives = emptyList()) }
        viewModelScope.launch {
            val routes = runCatching { NavigationSession.alternatives() }.getOrDefault(emptyList())
            _state.update { it.copy(loadingAlternatives = false, driveAlternatives = routes) }
        }
    }

    fun closeAlternatives() = _state.update { it.copy(driveAlternatives = null, loadingAlternatives = false) }

    /** The driver picked another route during the drive. */
    fun switchRoute(route: Route) {
        _state.update { it.copy(driveAlternatives = null) }
        NavigationSession.switchRoute(route)
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
                    _state.update {
                        it.copy(route = routes.first(), routeChoices = routes, selectedRoute = 0, tollEstimates = emptyList(),
                            routeTraffic = emptyList(), busy = false)
                    }
                    estimateTolls(routes)
                    loadRouteTraffic(routes)
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = com.roadpulse.app.i18n.currentStrings().routingFailed(e.message)) } }
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
        placesStore.load().planned.forEach { com.roadpulse.app.nav.LeaveReminders.cancel(getApplication(), it.id) }
        val cleared = placesStore.clear()
        _state.update { it.copy(saved = cleared, message = com.roadpulse.app.i18n.currentStrings().savedPlacesCleared) }
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
        seedTraffic()
        runCatching { NavigationService.start(getApplication()) }
            .onFailure { e ->
                NavigationSession.stop()
                _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().couldNotStartNavigation(e.message)) }
            }
    }

    /** Debug builds: navigate the current route with a simulated drive. */
    fun simulateDrive(config: DriveReplay.Config) {
        if (!BuildConfig.DEBUG) return
        if (_state.value.route == null || _state.value.destination == null) return
        NavigationSession.start(_state.value.route!!, _state.value.destination!!, persistDrive = false)
        seedTraffic()
        DriveReplay.start(config) // before the service starts, so it uses simulated fixes
        runCatching { NavigationService.start(getApplication()) }
            .onFailure { e ->
                DriveReplay.stop()
                NavigationSession.stop()
                _state.update { it.copy(message = "Could not start simulation: ${e.message}") }
            }
    }

    /** The drive starts with the traffic already fetched for the chosen route card. */
    private fun seedTraffic() {
        val info = _state.value.routeTraffic.getOrNull(_state.value.selectedRoute) ?: return
        NavigationSession.setTraffic(info.pieces, info.delaySeconds)
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
        if (System.currentTimeMillis() - lastTrafficFetch > 5_000) refreshTraffic()
    }

    private var lastTrafficFetch = 0L
    private var trafficJob: Job? = null

    /** Loads slow and jammed roads for the visible area (city zoom or closer). */
    private fun refreshTraffic() {
        val url = ServerSettings.backendUrl()
        val area = lastVisibleArea ?: return
        if (url.isEmpty() || area.width > 0.9 || area.height > 0.9) {
            if (_state.value.trafficLines.isNotEmpty()) _state.update { it.copy(trafficLines = emptyList()) }
            return
        }
        lastTrafficFetch = System.currentTimeMillis()
        trafficJob?.cancel()
        trafficJob = viewModelScope.launch {
            val lines = runCatching {
                com.roadpulse.app.data.TrafficClient(url).lines(area.minLat, area.minLon, area.maxLat, area.maxLon)
            }.getOrNull() ?: return@launch
            _state.update { it.copy(trafficLines = lines.filter { l -> l.level != "FREE" }) }
        }
    }

    /** Loads this device's profile (points, level, badges). */
    fun loadProfile() {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty()) {
            _state.update { it.copy(profileError = true) }
            return
        }
        viewModelScope.launch {
            runCatching { profileClient(url).get() }
                .onSuccess { p -> _state.update { it.copy(profile = p, profileError = false) } }
                .onFailure { _state.update { it.copy(profileError = true) } }
        }
    }

    fun saveNickname(name: String) {
        val url = ServerSettings.backendUrl()
        if (url.isEmpty()) return
        viewModelScope.launch {
            runCatching { profileClient(url).setNickname(name.trim().ifEmpty { null }) }
                .onSuccess { p -> _state.update { it.copy(profile = p, message = com.roadpulse.app.i18n.currentStrings().saved(p.nickname ?: "")) } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    /** A copy of this device's server data (JSON), for the share sheet. */
    fun exportMyData(onReady: (String) -> Unit) {
        val url = ServerSettings.backendUrl()
        val s = com.roadpulse.app.i18n.currentStrings()
        if (url.isEmpty()) {
            _state.update { it.copy(message = s.profileNeedsServer) }
            return
        }
        viewModelScope.launch {
            runCatching { privacyClient(url).export() }
                .onSuccess(onReady)
                .onFailure { _state.update { it.copy(message = s.exportFailed) } }
        }
    }

    /**
     * Deletes this device's data on the server (reports, votes, flags, nickname, share links) and
     * the personal data on the phone (places, planned drives, parking, device token, drive logs).
     * Settings such as language stay. Live traffic is switched off.
     */
    fun deleteMyData() {
        val app = getApplication<Application>()
        val url = ServerSettings.backendUrl()
        val s = com.roadpulse.app.i18n.currentStrings()
        viewModelScope.launch {
            if (url.isNotEmpty()) {
                val ok = runCatching { privacyClient(url).deleteAll() }
                    .recoverCatching { e -> if (e is com.roadpulse.app.data.ApiException && e.code == 401) Unit else throw e }
                    .isSuccess
                if (!ok) {
                    _state.update { it.copy(message = s.deleteFailed) }
                    return@launch
                }
            }
            com.roadpulse.app.nav.ShareSession.stop()
            com.roadpulse.app.nav.TrafficUploader.setEnabled(false)
            placesStore.load().planned.forEach { com.roadpulse.app.nav.LeaveReminders.cancel(app, it.id) }
            val cleared = placesStore.clear()
            app.getSharedPreferences("roadpulse_device", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            runCatching { File(app.getExternalFilesDir(null), "navlogs").deleteRecursively() }
            useReportsSource() // a fresh anonymous device next time it is needed
            _state.update { it.copy(saved = cleared, profile = null, message = s.dataDeleted) }
        }
    }

    private fun privacyClient(url: String) =
        com.roadpulse.app.data.PrivacyClient(com.roadpulse.app.data.DeviceApi(url, ServerSettings.tokenStore(getApplication(), url)))

    private fun profileClient(url: String) =
        com.roadpulse.app.data.ProfileClient(com.roadpulse.app.data.DeviceApi(url, ServerSettings.tokenStore(getApplication(), url)))

    // --- long press, stops, favourites, parking ------------------------------------------------

    /** The driver long-pressed the map: show actions for that point and look up its name. */
    fun onMapLongPress(point: GeoPoint) {
        val pin = Place(com.roadpulse.app.i18n.currentStrings().droppedPin, "%.5f, %.5f".format(java.util.Locale.ROOT, point.lat, point.lon), point)
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
            _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().waitingForLocation) }
            return
        }
        when {
            ui.navigating -> {
                NavigationSession.addStop(place.location, from)
                _state.update { it.copy(stops = it.stops + place, message = com.roadpulse.app.i18n.currentStrings().stopAdded(place.name)) }
            }
            ui.destination != null -> {
                _state.update { it.copy(stops = it.stops + place) }
                calculateRoute(from, ui.destination.location)
            }
            else -> _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().chooseDestinationFirst) }
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
        _state.update { it.copy(saved = saved, pinned = null, message = com.roadpulse.app.i18n.currentStrings().saved(name.trim())) }
    }

    /**
     * Plans the previewed drive for later: arrive by [arriveByMillis]. Saves it and sets the
     * "time to leave" reminder. Returns false when it is already too late to make it.
     */
    fun planDrive(arriveByMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val destination = _state.value.destination ?: return false
        val route = _state.value.route ?: return false
        val drive = com.roadpulse.app.data.PlannedDrive(nowMillis, destination, arriveByMillis, route.timeSeconds)
        val s = com.roadpulse.app.i18n.currentStrings()
        if (drive.leaveAtMillis < nowMillis) {
            _state.update { it.copy(message = s.tooLate) }
            return false
        }
        val saved = placesStore.addPlanned(drive, nowMillis)
        com.roadpulse.app.nav.LeaveReminders.schedule(getApplication(), drive, nowMillis)
        val at = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(drive.remindAtMillis))
        _state.update { it.copy(saved = saved, message = s.reminderSet(at)) }
        return true
    }

    fun removePlanned(drive: com.roadpulse.app.data.PlannedDrive) {
        com.roadpulse.app.nav.LeaveReminders.cancel(getApplication(), drive.id)
        _state.update { it.copy(saved = placesStore.removePlanned(drive.id), message = com.roadpulse.app.i18n.currentStrings().removed(drive.destination.name)) }
    }

    /** Opens the route of a planned drive (its reminder was tapped). */
    fun openPlanned(id: Long, from: GeoPoint?) {
        val drive = placesStore.load().planned.firstOrNull { it.id == id } ?: return
        onPlaceSelected(drive.destination, from)
    }

    fun removeFavourite(place: Place) {
        _state.update { it.copy(saved = placesStore.removeFavourite(place), message = com.roadpulse.app.i18n.currentStrings().removed(place.name)) }
    }

    /** Marks where the car is parked (the pinned point, or the current position). */
    fun parkHere(point: GeoPoint?) {
        val p = point ?: run {
            _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().locationUnavailable) }
            return
        }
        val saved = placesStore.setParking(com.roadpulse.app.data.ParkedCar(p, System.currentTimeMillis()))
        _state.update { it.copy(saved = saved, pinned = null, message = com.roadpulse.app.i18n.currentStrings().parkingSpotSaved) }
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
                .onSuccess { _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().thanks) } }
        }
    }

    fun submitReport(type: ReportType, location: GeoPoint?, subtype: String? = null, durationMinutes: Int? = null) {
        if (location == null) {
            _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().locationUnavailableReport) }
            return
        }
        viewModelScope.launch {
            try {
                reportRepository.submit(type, location, subtype, durationMinutes)
                _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().reported(type)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReportRejectedException) {
                _state.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(message = com.roadpulse.app.i18n.currentStrings().couldNotSendReport) }
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
