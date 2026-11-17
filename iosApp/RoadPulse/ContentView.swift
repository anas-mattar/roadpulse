import CoreLocation
import RoadPulseShared
import SwiftUI

/// The map screen. Parts are in ContentView+Explore (search card, saved places, planned drives),
/// ContentView+Drive (turn-by-turn screen) and ContentView+Settings (settings and privacy).
struct ContentView: View {
    @AppStorage("language") var languageCode = "en"
    @StateObject var config = AppConfig()
    @StateObject var model = SearchModel()
    @StateObject var location = LocationProvider()
    @StateObject var drive = DriveModel()
    @StateObject var prefs = DriveSettings()
    @StateObject var voice = VoiceSearch()
    @ObservedObject var notifications = NotificationRouter.shared
    @State var recenterToken = 0
    @State var showSettings = false
    @State var showReport = false
    /// Route choices as a list (true) or on the map (false).
    @State var listMode = false
    /// Drive sheet pulled up, and "Routes" in it.
    @State var sheetOpen = false
    @State var alternatives: [RouteSummary]?
    @State var showSharePreview = false
    @State var showSharedPanel = false
    @State var shareUrl: String?
    /// "Add a missing place" form for this long-pressed point.
    @State var showAddPlace = false
    @State var showPlaceProblem = false
    @State var addPlacePoint: SearchResult?
    @State var showLeaveLater = false
    @State var askFavouriteName = false
    @State var favouriteName = ""
    @State var favouritePlace: SearchResult?
    @State var overviewToken = 0
    @State var overviewShape: [GeoPoint] = []
    /// Ticks every minute (day/night map).
    @State var minuteTick = 0
    @State var selectedReport: ReportPin?
    @State var shareItem: ShareItem?
    @FocusState var searching: Bool

    var language: GuidanceLanguage {
        switch languageCode {
        case "ms": return .ms
        case "ar": return .ar
        default: return .en
        }
    }
    var strings: AppStrings { StringsKt.stringsFor(language: language) }
    var here: CLLocationCoordinate2D {
        if let s = drive.snapshot, s.hasPosition { return CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon) }
        return location.coordinate ?? MapView.kualaLumpur
    }
    /// Traffic colours on the route line: the drive's, or the selected route card's.
    var routeTraffic: [TrafficPolyline] {
        if let s = drive.snapshot { return s.trafficLines }
        return model.selected < model.routes.count ? model.routes[model.selected].trafficLines : []
    }

    /// The next turn on the map while driving (the road name travels in `name`).
    var turnMarker: SearchResult? {
        guard let s = drive.snapshot, s.hasUpcoming else { return nil }
        return SearchResult(name: s.upcomingStreet, detail: "", lat: s.upcomingLat, lon: s.upcomingLon)
    }

    /// Night map after sunset (or as chosen in Settings).
    var darkMap: Bool {
        _ = minuteTick
        return Choices.shared.darkMap(mapColorsName: prefs.mapColors, lat: here.latitude, lon: here.longitude, nowMillis: millis(Date()))
    }
    let brand = Color(red: 0.043, green: 0.498, blue: 0.471)
    private let refresh = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            MapView(styleUrl: config.styleUrl(dark: darkMap), routes: model.routes, selected: model.selected,
                    destination: model.destination, recenterToken: recenterToken, drive: drive.snapshot,
                    reports: model.reports, traffic: model.traffic,
                    onArea: { s, w, n, e in model.areaChanged(s, w, n, e, language: language) },
                    onReportTap: { id in selectedReport = model.reports.first { $0.id == id } },
                    northUp: prefs.northUp, showCompass: prefs.showCompass, tilted: !prefs.flatMap,
                    stops: model.stops, parking: model.book.parking, pinned: model.pinned,
                    overviewToken: overviewToken, overviewShape: overviewShape,
                    onLongPress: { lat, lon in
                        selectedReport = nil
                        Task { await model.pin(lat: lat, lon: lon, language: language, strings: strings) }
                    },
                    routeTraffic: routeTraffic,
                    turn: turnMarker)
                .ignoresSafeArea()

            Group {
                if let snapshot = drive.snapshot {
                    drivingOverlay(snapshot)
                } else {
                    exploreOverlay
                }
            }
            .padding(12)
            .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)

            if let message = model.message { toast(message) }
        }
        .onAppear {
            location.start()
            location.onFix = { fix in Task { @MainActor in drive.onFix(fix) } }
            drive.setLanguage(language)
            model.language = language
            model.configure(config)
            applyServers()
            applySettings()
        }
        .onChange(of: prefs.driveKey) { _ in
            applySettings()
            if !drive.isDriving && model.destination != nil {
                Task { await model.planRoutes(from: here, language: language) }
            }
        }
        .onChange(of: languageCode) { _ in
            drive.setLanguage(language)
            model.language = language
        }
        .onChange(of: config.key) { _ in
            model.configure(config)
            applyServers()
        }
        // Find the server (no typed address) at start and each time the app comes back to the front.
        .task { await config.findServer() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            Task { await config.findServer() }
        }
        .onChange(of: config.helpTraffic) { on in drive.setTrafficHelp(on) }
        .onChange(of: drive.isDriving) { driving in
            // Keep the screen on and the GPS running in the background while navigating.
            UIApplication.shared.isIdleTimerDisabled = driving
            location.setNavigating(driving)
            if !driving {
                model.clear()
                sheetOpen = false
                alternatives = nil
                shareUrl = nil
            }
            listMode = false
        }
        .onReceive(notifications.$openPlannedId) { id in
            guard let id = id else { return }
            notifications.openPlannedId = nil
            if let item = model.plannedDrives().first(where: { $0.id == id }) {
                Task { await model.choose(item.place, from: here, language: language, strings: strings) }
            }
        }
        .onReceive(refresh) { _ in
            minuteTick += 1
            Task { await model.refreshArea(language: language) }
        }
        .sheet(isPresented: $showSettings) { settings }
        .sheet(isPresented: $showReport) {
            ReportSheet(api: model.api, language: language, strings: strings, onSend: { type, subtype, minutes in
                showReport = false
                Task {
                    await model.report(type: type, subtype: subtype, durationMinutes: minutes, at: here,
                                       language: language, strings: strings)
                }
            }, onCancel: { showReport = false })
        }
        .sheet(isPresented: $showSharePreview) { sharePreview }
        .sheet(isPresented: $showAddPlace) {
            if let point = addPlacePoint {
                AddPlaceSheet(api: model.api, language: language, strings: strings, initialName: point.name == strings.droppedPin ? "" : point.name,
                              onAdd: { name, kind, note in
                                  showAddPlace = false
                                  Task { await model.addPlace(name: name, kind: kind, at: point, note: note, strings: strings) }
                              },
                              onCancel: { showAddPlace = false })
            }
        }
        .sheet(isPresented: $showPlaceProblem) {
            if let place = model.destination {
                PlaceProblemSheet(api: model.api, language: language, strings: strings, placeName: place.name,
                                  onSend: { kind, useMyPosition, note in
                                      showPlaceProblem = false
                                      // "Where I am now" only counts with a real GPS position.
                                      let position = useMyPosition ? location.coordinate : nil
                                      Task { await model.reportPlaceProblem(place: place, kind: kind, at: position, note: note, strings: strings) }
                                  },
                                  onCancel: { showPlaceProblem = false })
            }
        }
        .sheet(isPresented: $showSharedPanel) { sharedPanel }
        .sheet(isPresented: $showLeaveLater) { leaveLaterSheet }
        .sheet(item: $shareItem) { item in ActivityView(items: item.items) }
        .alert(strings.savePlace, isPresented: $askFavouriteName) {
            TextField(strings.placeNameHint, text: $favouriteName)
            Button(strings.save) { model.saveFavourite(name: favouriteName, place: favouritePlace, strings: strings) }
            Button(strings.cancel, role: .cancel) {}
        }
    }

    func applySettings() {
        model.routeOptions = prefs.routeOptions()
        drive.applySettings(prefs)
    }

    func applyServers() {
        drive.configure(routingUrl: config.routingUrl, backendUrl: config.backendUrl)
        drive.setTrafficHelp(config.helpTraffic)
    }

    private func toast(_ message: String) -> some View {
        VStack {
            Text(message).font(.subheadline).padding(.horizontal, 16).padding(.vertical, 10)
                .background(Color(white: 0.15)).foregroundColor(.white).clipShape(Capsule())
                .padding(.top, 60)
            Spacer()
        }
        .transition(.opacity)
        .task(id: message) {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            model.message = nil
        }
    }

    func circleButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(brand)
                .frame(width: 44, height: 44)
                .background(Color(.systemBackground))
                .clipShape(Circle())
                .shadow(radius: 3)
        }
        .accessibilityLabel(label)
    }

    func pill(_ text: String, icon: String, trailing: String?, background: Color, foreground: Color = .white) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
            Text(text).bold()
            if let t = trailing { Text(t).opacity(0.85) }
        }
        .font(.footnote)
        .padding(.horizontal, 12).padding(.vertical, 6)
        .background(background).foregroundColor(foreground)
        .clipShape(Capsule())
    }

    func chip(_ text: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                Text(text).lineLimit(1)
            }
            .font(.subheadline.bold())
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(brand.opacity(0.12)).foregroundColor(brand).clipShape(Capsule())
        }
    }
}
