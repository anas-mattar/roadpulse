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
    @ObservedObject var notifications = NotificationRouter.shared
    @State var recenterToken = 0
    @State var showSettings = false
    @State var showReport = false
    @State var showProfile = false
    @State var showLeaveLater = false
    @State var askFavouriteName = false
    @State var favouriteName = ""
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
    let indigo = Color(red: 0.33, green: 0.29, blue: 0.72)
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
            model.configure(config)
            applyServers()
        }
        .onChange(of: languageCode) { _ in drive.setLanguage(language) }
        .onChange(of: config.key) { _ in
            model.configure(config)
            applyServers()
        }
        .onChange(of: config.helpTraffic) { on in drive.setTrafficHelp(on) }
        .onChange(of: drive.isDriving) { driving in
            // Keep the screen on and the GPS running in the background while navigating.
            UIApplication.shared.isIdleTimerDisabled = driving
            location.setNavigating(driving)
            if !driving { model.clear() }
        }
        .onReceive(notifications.$openPlannedId) { id in
            guard let id = id else { return }
            notifications.openPlannedId = nil
            if let item = model.plannedDrives().first(where: { $0.id == id }) {
                Task { await model.choose(item.place, from: here, language: language, strings: strings) }
            }
        }
        .onReceive(refresh) { _ in Task { await model.refreshArea(language: language) } }
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
        .sheet(isPresented: $showProfile) {
            ProfileView(model: model, language: language, strings: strings, onClose: { showProfile = false })
        }
        .sheet(isPresented: $showLeaveLater) { leaveLaterSheet }
        .sheet(item: $shareItem) { item in ActivityView(items: item.items) }
        .alert(strings.savePlace, isPresented: $askFavouriteName) {
            TextField(strings.placeNameHint, text: $favouriteName)
            Button(strings.save) { model.saveFavourite(name: favouriteName, strings: strings) }
            Button(strings.cancel, role: .cancel) {}
        }
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
                .foregroundColor(indigo)
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
            .background(indigo.opacity(0.12)).foregroundColor(indigo).clipShape(Capsule())
        }
    }
}
