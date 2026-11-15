import CoreLocation
import RoadPulseShared
import SwiftUI

/// Search, route choices, community reports, places and traffic for the map screen, on the same
/// shared logic as Android.
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
        Task { await refreshArea() }
    }

    func search(near: CLLocationCoordinate2D) async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else { return }
        busy = true
        errorText = nil
        resultsTitle = nil
        do {
            results = try await api.search(query: text, nearLat: near.latitude, nearLon: near.longitude)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    /// Fuel, parking, food… near the position, from the RoadPulse server.
    func searchCategory(_ name: String, label: String, near: CLLocationCoordinate2D, language: GuidanceLanguage, strings: AppStrings) async {
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

    func choose(_ place: SearchResult, from: CLLocationCoordinate2D, language: GuidanceLanguage) async {
        destination = place
        results = []
        resultsTitle = nil
        query = place.name
        routes = []
        selected = 0
        busy = true
        errorText = nil
        do {
            routes = try await api.routes(fromLat: from.latitude, fromLon: from.longitude,
                                          toLat: place.lat, toLon: place.lon, language: language)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    func clear() {
        query = ""
        results = []
        resultsTitle = nil
        destination = nil
        routes = []
        selected = 0
        errorText = nil
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

    func report(type: String, at: CLLocationCoordinate2D, language: GuidanceLanguage, strings: AppStrings) async {
        do {
            if let pin = try await api.report(typeName: type, lat: at.latitude, lon: at.longitude, subtype: nil, language: language) {
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
}

struct ContentView: View {
    @AppStorage("language") private var languageCode = "en"
    @StateObject private var config = AppConfig()
    @StateObject private var model = SearchModel()
    @StateObject private var location = LocationProvider()
    @StateObject private var drive = DriveModel()
    @State private var recenterToken = 0
    @State private var showSettings = false
    @State private var showReport = false
    @State private var selectedReport: ReportPin?
    @State private var serverCheck: String?
    @FocusState private var searching: Bool

    private var language: GuidanceLanguage {
        switch languageCode {
        case "ms": return .ms
        case "ar": return .ar
        default: return .en
        }
    }
    private var strings: AppStrings { StringsKt.stringsFor(language: language) }
    private var here: CLLocationCoordinate2D {
        if let s = drive.snapshot, s.hasPosition { return CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon) }
        return location.coordinate ?? MapView.kualaLumpur
    }
    private let indigo = Color(red: 0.33, green: 0.29, blue: 0.72)
    private let refresh = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack {
            MapView(styleUrl: config.styleUrl, routes: model.routes, selected: model.selected, destination: model.destination,
                    recenterToken: recenterToken, drive: drive.snapshot, reports: model.reports, traffic: model.traffic,
                    onArea: { s, w, n, e in model.areaChanged(s, w, n, e, language: language) },
                    onReportTap: { id in selectedReport = model.reports.first { $0.id == id } })
                .ignoresSafeArea()

            Group {
                if let snapshot = drive.snapshot {
                    drivingOverlay(snapshot)
                } else {
                    VStack(spacing: 10) {
                        HStack {
                            Spacer()
                            circleButton("gearshape.fill", label: strings.settings) { showSettings = true }
                        }
                        Spacer()
                        HStack(alignment: .bottom) {
                            if model.hasServer { reportButton }
                            Spacer()
                            circleButton("location.fill", label: strings.showMyLocation) { recenterToken += 1 }
                        }
                        if let pin = selectedReport { reportCard(pin) } else { card }
                    }
                }
            }
            .padding(12)
            .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)

            if let message = model.message {
                VStack {
                    Text(message).font(.subheadline).padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Color(white: 0.15)).foregroundColor(.white).clipShape(Capsule())
                        .padding(.top, 60)
                    Spacer()
                }
                .transition(.opacity)
                .task {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    model.message = nil
                }
            }
        }
        .onAppear {
            location.start()
            location.onFix = { fix in Task { @MainActor in drive.onFix(fix) } }
            drive.setLanguage(language)
            model.configure(config)
            drive.configure(routingUrl: config.routingUrl, backendUrl: config.backendUrl)
        }
        .onChange(of: languageCode) { _ in drive.setLanguage(language) }
        .onChange(of: config.key) { _ in
            model.configure(config)
            drive.configure(routingUrl: config.routingUrl, backendUrl: config.backendUrl)
        }
        .onChange(of: drive.isDriving) { driving in
            // Keep the screen on and the GPS running in the background while navigating.
            UIApplication.shared.isIdleTimerDisabled = driving
            location.setNavigating(driving)
            if !driving { model.clear() }
        }
        .onReceive(refresh) { _ in Task { await model.refreshArea(language: language) } }
        .sheet(isPresented: $showSettings) { settings }
        .sheet(isPresented: $showReport) { reportSheet }
    }

    private func circleButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(indigo)
                .frame(width: 44, height: 44)
                .background(Color(.systemBackground))
                .clipShape(Circle())
                .shadow(radius: 3)
        }
        .accessibilityLabel(label)
    }

    private func pill(_ text: String, icon: String, trailing: String?, background: Color, foreground: Color = .white) -> some View {
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

    // MARK: Driving

    private func drivingOverlay(_ s: DriveSnapshot) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 14) {
                Image(systemName: turnSymbol(s.turnIcon))
                    .font(.system(size: 38, weight: .bold))
                    .frame(width: 52)
                VStack(alignment: .leading, spacing: 2) {
                    if s.distanceToTurnM >= 0 {
                        Text(strings.distance(km: s.distanceToTurnM / 1000)).font(.system(size: 30, weight: .semibold))
                    }
                    Text(s.instruction).font(.subheadline).opacity(0.85).lineLimit(2)
                }
                Spacer(minLength: 0)
            }
            .foregroundColor(.white)
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background(indigo)
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .shadow(radius: 5)

            // Lanes: always left to right, as on the road.
            if !s.lanes.isEmpty && s.distanceToTurnM >= 0 && s.distanceToTurnM <= 1500 {
                HStack(spacing: 4) {
                    ForEach(Array(s.lanes.enumerated()), id: \.offset) { _, lane in
                        Image(systemName: turnSymbol(lane.icon))
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(Color.white.opacity(laneOpacity(lane)))
                            .frame(width: 34, height: 34)
                    }
                }
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(Color(red: 0.15, green: 0.13, blue: 0.36))
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .environment(\.layoutDirection, .leftToRight)
            }

            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    if let label = s.reportLabel, s.reportAheadM >= 0 {
                        pill(label, icon: "exclamationmark.triangle.fill", trailing: strings.distance(km: s.reportAheadM / 1000),
                             background: Color(hex: s.reportColorHex ?? "#4C4F69"))
                    }
                    if let label = s.cameraLabel, s.cameraAheadM >= 0 {
                        pill(label, icon: "camera.fill", trailing: strings.distance(km: s.cameraAheadM / 1000),
                             background: Color(red: 0.15, green: 0.13, blue: 0.36))
                    }
                    if let label = s.schoolLabel, s.schoolAheadM >= 0 {
                        pill(label, icon: "graduationcap.fill", trailing: strings.distance(km: s.schoolAheadM / 1000),
                             background: Color(red: 0.98, green: 0.78, blue: 0.46), foreground: Color(red: 0.39, green: 0.22, blue: 0.02))
                    }
                    if let status = s.status {
                        pill(status, icon: "info.circle.fill", trailing: nil, background: Color.orange.opacity(0.9), foreground: .black)
                    }
                }
                Spacer()
                VStack(spacing: 10) {
                    circleButton(drive.muted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                                 label: drive.muted ? strings.unmuteVoice : strings.muteVoice) { drive.muted.toggle() }
                    circleButton("location.fill", label: strings.showMyLocation) { recenterToken += 1 }
                }
            }

            Spacer()

            HStack(alignment: .bottom, spacing: 8) {
                if s.speedKmh >= 0 {
                    VStack(spacing: 0) {
                        Text("\(s.speedKmh)").font(.system(size: 22, weight: .semibold))
                        Text(strings.kmh).font(.system(size: 10))
                    }
                    .foregroundColor(isSpeeding(s) ? Color.red : Color.white)
                    .frame(width: 64, height: 64)
                    .background(Color(white: 0.17)).clipShape(Circle())
                }
                if s.speedLimitKph > 0 {
                    Text("\(s.speedLimitKph)").font(.system(size: 17, weight: .bold)).foregroundColor(.black)
                        .frame(width: 46, height: 46)
                        .background(Color.white).clipShape(Circle())
                        .overlay(Circle().stroke(Color.red, lineWidth: 5))
                }
                Spacer()
                if model.hasServer {
                    Button { showReport = true } label: {
                        Image(systemName: "exclamationmark.bubble.fill")
                            .font(.system(size: 22)).foregroundColor(Color(red: 0.39, green: 0.22, blue: 0.02))
                            .frame(width: 56, height: 56)
                            .background(Color(red: 0.98, green: 0.78, blue: 0.46)).clipShape(Circle()).shadow(radius: 3)
                    }
                    .accessibilityLabel(strings.report)
                }
            }

            if s.fasterRouteSavesMinutes > 0 {
                VStack(alignment: .leading, spacing: 10) {
                    Text(strings.fasterRoute(savedMinutes: s.fasterRouteSavesMinutes)).font(.headline)
                    HStack(spacing: 10) {
                        Button { drive.takeFasterRoute() } label: {
                            Text(strings.takeRoute).bold().frame(maxWidth: .infinity).frame(height: 46)
                                .background(Color.white).foregroundColor(Color(red: 0.06, green: 0.43, blue: 0.34)).clipShape(Capsule())
                        }
                        Button { drive.keepRoute() } label: {
                            Text(strings.keepRoute).frame(maxWidth: .infinity).frame(height: 46)
                                .overlay(Capsule().stroke(Color.white, lineWidth: 1)).foregroundColor(.white)
                        }
                    }
                }
                .foregroundColor(.white)
                .padding(14)
                .background(Color(red: 0.06, green: 0.43, blue: 0.34))
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .task(id: s.fasterRouteSavesMinutes) {
                    // Closes by itself (keeping the route) after 20 seconds.
                    try? await Task.sleep(nanoseconds: 20_000_000_000)
                    let saves = drive.snapshot?.fasterRouteSavesMinutes ?? 0
                    if saves > 0 { drive.keepRoute() }
                }
            }

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    // Arrival includes the delay from live traffic on the route ahead.
                    Text(arrival(s), style: .time)
                        .font(.system(size: 26, weight: .semibold))
                    Text(tripLine(s))
                        .font(.subheadline).opacity(0.8)
                }
                Spacer()
                Button { drive.stop() } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                        .frame(width: 52, height: 52).background(Color.red).clipShape(Circle())
                }
                .accessibilityLabel(strings.stopNavigation)
            }
            .foregroundColor(.white)
            .padding(16)
            .background(Color(white: 0.2))
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .shadow(radius: 5)
        }
    }

    private func laneOpacity(_ lane: LaneArrow) -> Double {
        if lane.recommended { return 1 }
        return lane.usable ? 0.6 : 0.25
    }

    private func isSpeeding(_ s: DriveSnapshot) -> Bool {
        let limit = Int(s.speedLimitKph)
        return limit > 0 && Int(s.speedKmh) > limit + 3
    }

    /// Arrival time, including the delay from live traffic on the route ahead.
    private func arrival(_ s: DriveSnapshot) -> Date {
        let minutes = Int(s.remainingMinutes) + Int(s.trafficDelayMinutes)
        return Date().addingTimeInterval(Double(minutes) * 60)
    }

    private func tripLine(_ s: DriveSnapshot) -> String {
        var parts: [String] = [strings.duration(minutes: s.remainingMinutes), strings.distance(km: s.remainingKm)]
        if s.trafficDelayMinutes > 0 { parts.append(strings.trafficDelay(minutes: s.trafficDelayMinutes)) }
        return parts.joined(separator: " · ")
    }

    // MARK: Reports

    private var reportButton: some View {
        Button { showReport = true } label: {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                Text(strings.report).bold()
            }
            .font(.subheadline)
            .foregroundColor(Color(red: 0.39, green: 0.22, blue: 0.02))
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(Color(red: 0.98, green: 0.78, blue: 0.46))
            .clipShape(Capsule())
            .shadow(radius: 3)
        }
    }

    /// Five large buttons; one tap sends the report at the current position.
    private var reportSheet: some View {
        let types = model.api.reportTypes(language: language)
        return VStack(alignment: .leading, spacing: 14) {
            Text(strings.report).font(.title2).bold()
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(types, id: \.self) { t in
                    Button {
                        showReport = false
                        Task { await model.report(type: t[0], at: here, language: language, strings: strings) }
                    } label: {
                        VStack(spacing: 6) {
                            Image(systemName: reportSymbol(t[0])).font(.system(size: 30))
                            Text(t[1]).font(.subheadline).bold().multilineTextAlignment(.center)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity).frame(height: 100)
                        .background(Color(hex: t[2]))
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                    }
                }
            }
            Button(strings.cancel) { showReport = false }.frame(maxWidth: .infinity)
        }
        .padding(20)
        .presentationDetents([.medium])
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }

    private func reportCard(_ pin: ReportPin) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: reportSymbol(pin.typeName))
                    .foregroundColor(.white).frame(width: 44, height: 44)
                    .background(Color(hex: pin.colorHex)).clipShape(Circle())
                VStack(alignment: .leading) {
                    Text(pin.label).font(.headline)
                    if !pin.detail.isEmpty { Text(pin.detail).font(.footnote).foregroundColor(.secondary) }
                }
                Spacer()
                Button { selectedReport = nil } label: { Image(systemName: "xmark").foregroundColor(.secondary) }
                    .accessibilityLabel(strings.close)
            }
            if pin.own {
                Text(strings.yourReport).font(.footnote).foregroundColor(.secondary)
            } else {
                Text(strings.isItStillThere).font(.subheadline)
                HStack(spacing: 10) {
                    Button {
                        selectedReport = nil
                        Task { await model.vote(pin, stillThere: true, language: language, strings: strings) }
                    } label: {
                        Label(strings.stillThere, systemImage: "hand.thumbsup.fill")
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .background(indigo).foregroundColor(.white).clipShape(Capsule())
                    }
                    Button {
                        selectedReport = nil
                        Task { await model.vote(pin, stillThere: false, language: language, strings: strings) }
                    } label: {
                        Label(strings.notThere, systemImage: "hand.thumbsdown.fill")
                            .frame(maxWidth: .infinity).frame(height: 44)
                            .overlay(Capsule().stroke(Color.secondary, lineWidth: 1)).foregroundColor(.primary)
                    }
                }
            }
        }
        .padding(16)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
    }

    // MARK: Explore and preview

    /// The bottom card: search box, then categories, results or the route choices.
    private var card: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(indigo)
                TextField(strings.whereTo, text: $model.query)
                    .focused($searching)
                    .submitLabel(.search)
                    .onSubmit { Task { await model.search(near: here) } }
                if model.busy { ProgressView() }
                if !model.query.isEmpty || model.destination != nil || !model.results.isEmpty {
                    Button { model.clear() } label: { Image(systemName: "xmark.circle.fill").foregroundColor(.secondary) }
                        .accessibilityLabel(strings.clear)
                }
            }
            .padding(12)
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 16))

            if let error = model.errorText {
                Text(error).font(.footnote).foregroundColor(.red)
            }

            if model.hasServer && model.query.isEmpty && model.results.isEmpty && model.destination == nil {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(model.api.categories(language: language), id: \.self) { c in
                            Button {
                                Task {
                                    await model.searchCategory(c[0], label: strings.categoryNearby(c: categoryFor(c[0])), near: here,
                                                               language: language, strings: strings)
                                }
                            } label: {
                                Text(c[1]).font(.subheadline).bold()
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                    .background(indigo.opacity(0.12)).foregroundColor(indigo).clipShape(Capsule())
                            }
                        }
                    }
                }
            }

            if !model.results.isEmpty {
                if let title = model.resultsTitle { Text(title).font(.headline) }
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        ForEach(Array(model.results.enumerated()), id: \.offset) { _, place in
                            Button {
                                searching = false
                                Task { await model.choose(place, from: here, language: language) }
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(place.name).foregroundColor(.primary)
                                    if !place.detail.isEmpty {
                                        Text(place.detail).font(.footnote).foregroundColor(.secondary).lineLimit(1)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 8)
                            }
                        }
                    }
                }
                .frame(maxHeight: 220)
            } else if !model.routes.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(model.routes.enumerated()), id: \.offset) { index, route in
                            routeCard(route, isSelected: index == model.selected)
                                .onTapGesture { model.selected = index }
                        }
                    }
                }
                if let place = model.destination, model.selected < model.routes.count {
                    HStack(spacing: 10) {
                        // Try the drive at a desk: the car follows the route by itself.
                        Button { drive.start(route: model.routes[model.selected], to: place, simulated: true) } label: {
                            Image(systemName: "play.fill").foregroundColor(.primary)
                                .frame(width: 52, height: 52)
                                .background(Color(.secondarySystemBackground)).clipShape(Circle())
                        }
                        .accessibilityLabel(strings.simulateDrive)
                        Button { drive.start(route: model.routes[model.selected], to: place, simulated: false) } label: {
                            HStack {
                                Image(systemName: "location.north.fill")
                                Text(strings.goNow).font(.headline)
                            }
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity).frame(height: 52)
                            .background(indigo).clipShape(Capsule())
                        }
                    }
                }
            }

            Text("© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap")
                .font(.system(size: 9)).foregroundColor(.secondary)
        }
        .padding(14)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
    }

    private func categoryFor(_ name: String) -> PlaceCategory {
        switch name {
        case "PARKING": return .parking
        case "FOOD": return .food
        case "EV_CHARGING": return .evCharging
        case "REST_AREA": return .restArea
        default: return .fuel
        }
    }

    private func routeCard(_ route: RouteSummary, isSelected: Bool) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(route.tag)
                .font(.caption2).bold()
                .padding(.horizontal, 7).padding(.vertical, 2)
                .background(isSelected ? indigo : Color(.systemBackground))
                .foregroundColor(isSelected ? .white : .secondary)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            Text(strings.duration(minutes: route.minutes)).font(.title2).bold()
            Text(strings.distance(km: route.km)).font(.footnote).foregroundColor(.secondary)
            if let via = route.via {
                Text(strings.via(road: via)).font(.caption).foregroundColor(.secondary).lineLimit(1)
            }
            if let toll = route.tollLabel {
                Text(toll).font(.caption).bold().foregroundColor(.orange).lineLimit(1)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .frame(width: 160, alignment: .leading)
        .background(isSelected ? indigo.opacity(0.12) : Color(.secondarySystemBackground))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(isSelected ? indigo : Color.clear, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    // MARK: Settings

    private var settings: some View {
        NavigationStack {
            Form {
                Section(strings.language) {
                    Picker(strings.language, selection: $languageCode) {
                        Text("English").tag("en")
                        Text("Bahasa Melayu").tag("ms")
                        Text("العربية").tag("ar")
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                // Developer setting (English, like on Android): the RoadPulse servers on the laptop.
                Section {
                    Toggle("Use my laptop's server", isOn: $config.useLaptop)
                    if config.useLaptop {
                        TextField("Laptop address, e.g. 192.168.1.20", text: $config.laptopAddress)
                            .keyboardType(.numbersAndPunctuation)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                        Toggle("Maps from my laptop", isOn: $config.laptopMaps)
                        Button("Test connection") {
                            serverCheck = "…"
                            Task {
                                let reply = try? await config.makeApi().serverReachable()
                                let ok = reply?.boolValue ?? false
                                serverCheck = ok ? "Connected to \(config.backendUrl)" : "No answer from \(config.backendUrl)"
                            }
                        }
                        if let check = serverCheck { Text(check).font(.footnote).foregroundColor(.secondary) }
                    }
                } header: {
                    Text("Server")
                } footer: {
                    Text("Routing, reports, places, tolls and traffic from the RoadPulse servers on your laptop. The iPhone and the laptop must be on the same Wi-Fi; run open-iphone-access.ps1 on the laptop once (see docs/IOS.md).")
                }
            }
            .navigationTitle(strings.settings)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(strings.done) { showSettings = false }
                }
            }
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }
}
