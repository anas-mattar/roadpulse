import CoreLocation
import RoadPulseShared
import SwiftUI

/// Search, route choices, saved places, community reports, places and traffic for the map screen,
/// on the same shared logic as Android.
@MainActor
final class SearchModel: ObservableObject {
    @Published var query = ""
    @Published var results: [SearchResult] = []
    /// "Fuel nearby" when the results are places of a category.
    @Published var resultsTitle: String?
    @Published var destination: SearchResult?
    @Published var routes: [RouteSummary] = []
    @Published var selected = 0
    @Published var busy = false
    @Published var errorText: String?
    @Published var message: String?
    @Published var reports: [ReportPin] = []
    @Published var traffic: [TrafficPolyline] = []
    /// Searching for the Home ("home") or Work ("work") address instead of a destination.
    @Published var shortcutTarget: String?
    @Published var profile: ProfileCard?
    /// Stops on the way to the destination, in order.
    @Published var stops: [SearchResult] = []
    /// A long-pressed point and its name, with actions (drive here, add stop, parked here, save).
    @Published var pinned: SearchResult?
    /// App language (for the details of drivers' places in search).
    var language: GuidanceLanguage = .en
    /// Vehicle and roads to avoid (from Settings).
    var routeOptions = Choices.shared.routeOptions(vehicleName: "CAR", avoidTolls: false, avoidHighways: false,
                                                    avoidFerries: false, allowUnpaved: false)

    /// Home, Work, favourites, recent places and planned drives (only on this phone).
    let book = SavedPlacesBook()
    private(set) var api: RoadPulseApi
    private var configKey = ""
    private var area: (Double, Double, Double, Double)?
    private var areaTask: Task<Void, Never>?

    init() {
        let config = AppConfig()
        api = config.makeApi()
        configKey = config.key
    }

    var hasServer: Bool { api.hasServer }

    /// Rebuilds the clients when the servers change in Settings.
    func configure(_ config: AppConfig) {
        guard config.key != configKey else { return }
        configKey = config.key
        api = config.makeApi()
        reports = []
        traffic = []
        profile = nil
        Task { await refreshArea() }
    }

    // MARK: Search and routes

    private var typingTask: Task<Void, Never>?

    /// The search text changed: search after a short pause (not for the name of a chosen place).
    func queryChanged(near: CLLocationCoordinate2D) {
        typingTask?.cancel()
        let text = query.trimmingCharacters(in: .whitespaces)
        if text.count < 3 || destination?.name == query { return }
        typingTask = Task {
            try? await Task.sleep(nanoseconds: 600_000_000)
            if !Task.isCancelled && query.trimmingCharacters(in: .whitespaces) == text { await search(near: near) }
        }
    }

    func search(near: CLLocationCoordinate2D) async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else { return }
        busy = true
        errorText = nil
        resultsTitle = nil
        do {
            results = try await api.search(query: text, nearLat: near.latitude, nearLon: near.longitude, language: language)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    /// Fuel, parking, food… near the position, from the RoadPulse server.
    func searchCategory(_ name: String, label: String, near: CLLocationCoordinate2D, language: GuidanceLanguage) async {
        busy = true
        errorText = nil
        let now = Calendar.current.dateComponents([.weekday, .hour, .minute], from: Date())
        // Calendar weekday: 1 = Sunday … 7 = Saturday; shared logic: 0 = Monday … 6 = Sunday.
        let day = ((now.weekday ?? 2) + 5) % 7
        do {
            let places = try await api.places(categoryName: name, nearLat: near.latitude, nearLon: near.longitude, language: language,
                                              dayIndex: Int32(day), hour: Int32(now.hour ?? 0), minute: Int32(now.minute ?? 0))
            results = places.map { SearchResult(name: $0.name, detail: $0.detail, lat: $0.lat, lon: $0.lon) }
            resultsTitle = results.isEmpty ? nil : label
            if results.isEmpty { message = label }
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    /// A place was picked: save it as Home/Work when asked for, otherwise show the routes to it.
    func choose(_ place: SearchResult, from: CLLocationCoordinate2D, language: GuidanceLanguage, strings: AppStrings) async {
        if let target = shortcutTarget {
            if target == "home" { book.setHome(place: place) } else { book.setWork(place: place) }
            message = strings.shortcutSaved(home: target == "home")
            clear()
            return
        }
        book.addRecent(place: place)
        destination = place
        results = []
        resultsTitle = nil
        query = place.name
        await planRoutes(from: from, language: language)
    }

    /// Plans (again) to the destination via the stops, with the current route options.
    func planRoutes(from: CLLocationCoordinate2D, language: GuidanceLanguage) async {
        guard let place = destination else { return }
        routes = []
        selected = 0
        busy = true
        errorText = nil
        do {
            routes = try await api.routes(fromLat: from.latitude, fromLon: from.longitude, toLat: place.lat, toLon: place.lon,
                                          stops: stops, options: routeOptions, language: language)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    func addStop(_ place: SearchResult, from: CLLocationCoordinate2D, language: GuidanceLanguage) async {
        stops.append(place)
        await planRoutes(from: from, language: language)
    }

    func removeStop(at index: Int, from: CLLocationCoordinate2D, language: GuidanceLanguage) async {
        guard index < stops.count else { return }
        stops.remove(at: index)
        await planRoutes(from: from, language: language)
    }

    /// The driver long-pressed the map: show the point at once, then its name.
    func pin(lat: Double, lon: Double, language: GuidanceLanguage, strings: AppStrings) async {
        pinned = SearchResult(name: strings.droppedPin, detail: "", lat: lat, lon: lon)
        if let named = try? await api.placeAt(lat: lat, lon: lon, language: language),
           let current = pinned, current.lat == lat, current.lon == lon {
            pinned = named
        }
    }

    func clear() {
        query = ""
        results = []
        resultsTitle = nil
        destination = nil
        routes = []
        selected = 0
        errorText = nil
        shortcutTarget = nil
        stops = []
    }

    // MARK: Saved places

    func saveFavourite(name: String, place: SearchResult?, strings: AppStrings) {
        guard let place = place, !name.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        book.addFavourite(name: name, place: place)
        objectWillChange.send()
        message = strings.saved(name: name)
    }

    func removeFavourite(_ place: SearchResult, strings: AppStrings) {
        book.removeFavourite(place: place)
        objectWillChange.send()
        message = strings.removed(name: place.name)
    }

    /// "Add a missing place": the driver finds it in search at once, others after review.
    func addPlace(name: String, kind: String, at point: SearchResult, note: String, strings: AppStrings) async {
        let ok = (try? await api.addPlace(name: name, kindName: kind, lat: point.lat, lon: point.lon, note: note))?.boolValue ?? false
        message = ok ? strings.placeAdded : strings.placeAddFailed
    }

    func setParking(_ point: SearchResult, strings: AppStrings) {
        book.setParking(lat: point.lat, lon: point.lon, nowMillis: millis(Date()))
        objectWillChange.send()
        message = strings.parkingSpotSaved
    }

    func clearParking() {
        book.clearParking()
        objectWillChange.send()
    }

    func plannedDrives() -> [PlannedItem] { book.planned(nowMillis: millis(Date())) }

    func removePlanned(_ item: PlannedItem) {
        book.removePlanned(id: item.id)
        LeaveReminders.cancel(item.id)
        objectWillChange.send()
    }

    func clearSavedPlaces(strings: AppStrings) {
        book.clear()
        LeaveReminders.cancelAll()
        objectWillChange.send()
        message = strings.savedPlacesCleared
    }

    // MARK: Reports and traffic

    /// The map moved: reload reports and traffic for the new area (after a short pause).
    func areaChanged(_ south: Double, _ west: Double, _ north: Double, _ east: Double, language: GuidanceLanguage) {
        area = (south, west, north, east)
        areaTask?.cancel()
        areaTask = Task {
            try? await Task.sleep(nanoseconds: 800_000_000)
            if !Task.isCancelled { await refreshArea(language: language) }
        }
    }

    func refreshArea(language: GuidanceLanguage = .en) async {
        guard api.hasServer, let a = area else { return }
        if let pins = try? await api.reportsIn(minLat: a.0, minLon: a.1, maxLat: a.2, maxLon: a.3, language: language) {
            reports = pins
        }
        if let lines = try? await api.traffic(minLat: a.0, minLon: a.1, maxLat: a.2, maxLon: a.3) {
            traffic = lines
        }
    }

    func report(type: String, subtype: String?, durationMinutes: Int32, at: CLLocationCoordinate2D,
                language: GuidanceLanguage, strings: AppStrings) async {
        do {
            if let pin = try await api.report(typeName: type, lat: at.latitude, lon: at.longitude, subtype: subtype,
                                              durationMinutes: durationMinutes, language: language) {
                reports.removeAll { $0.id == pin.id }
                reports.append(pin)
                message = strings.thanks
            }
        } catch {
            message = strings.couldNotSendReport
        }
    }

    func vote(_ pin: ReportPin, stillThere: Bool, language: GuidanceLanguage, strings: AppStrings) async {
        do {
            try await api.vote(reportId: pin.id, stillThere: stillThere)
            message = strings.thanks
            await refreshArea(language: language)
        } catch {
            message = strings.couldNotSendReport
        }
    }

    // MARK: Profile

    func loadProfile(language: GuidanceLanguage) async {
        guard api.hasServer else { return }
        if let card = try? await api.profile(language: language) { profile = card }
    }

    func setNickname(_ name: String, language: GuidanceLanguage, strings: AppStrings) async {
        do {
            if let card = try await api.setNickname(name: name, language: language) {
                profile = card
                message = strings.saved(name: name.isEmpty ? strings.nickname : name)
            }
        } catch {
            message = error.localizedDescription
        }
    }
}
