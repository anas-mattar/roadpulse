import CoreLocation
import RoadPulseShared
import SwiftUI

/// Step 3 of the iPhone app: the map with your position, place search and route choices drawn on
/// the map, using the same shared logic as Android. Turn-by-turn navigation comes next.
@MainActor
final class SearchModel: ObservableObject {
    @Published var query = ""
    @Published var results: [SearchResult] = []
    @Published var destination: SearchResult?
    @Published var routes: [RouteSummary] = []
    @Published var selected = 0
    @Published var busy = false
    @Published var errorText: String?

    // Public servers for now; the laptop's servers become a setting later (see docs/IOS.md).
    private let api = RoadPulseApi(geocodingUrl: "https://photon.komoot.io", routingUrl: "https://valhalla1.openstreetmap.de")

    func search(near: CLLocationCoordinate2D) async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else { return }
        busy = true
        errorText = nil
        do {
            results = try await api.search(query: text, nearLat: near.latitude, nearLon: near.longitude)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    func choose(_ place: SearchResult, from: CLLocationCoordinate2D, language: GuidanceLanguage) async {
        destination = place
        results = []
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
        destination = nil
        routes = []
        selected = 0
        errorText = nil
    }
}

struct ContentView: View {
    @AppStorage("language") private var languageCode = "en"
    @StateObject private var model = SearchModel()
    @StateObject private var location = LocationProvider()
    @State private var recenterToken = 0
    @State private var showSettings = false
    @FocusState private var searching: Bool

    private var language: GuidanceLanguage {
        switch languageCode {
        case "ms": return .ms
        case "ar": return .ar
        default: return .en
        }
    }
    private var strings: AppStrings { StringsKt.stringsFor(language: language) }
    private var here: CLLocationCoordinate2D { location.coordinate ?? MapView.kualaLumpur }
    private let indigo = Color(red: 0.33, green: 0.29, blue: 0.72)

    var body: some View {
        ZStack {
            MapView(routes: model.routes, selected: model.selected, destination: model.destination, recenterToken: recenterToken)
                .ignoresSafeArea()

            VStack(spacing: 10) {
                HStack {
                    Spacer()
                    circleButton("gearshape.fill", label: strings.settings) { showSettings = true }
                }
                Spacer()
                HStack {
                    Spacer()
                    circleButton("location.fill", label: strings.showMyLocation) { recenterToken += 1 }
                }
                card
            }
            .padding(12)
            .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
        }
        .onAppear { location.start() }
        .sheet(isPresented: $showSettings) { settings }
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

    /// The bottom card: search box, then results or the route choices.
    private var card: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "magnifyingglass").foregroundColor(indigo)
                TextField(strings.whereTo, text: $model.query)
                    .focused($searching)
                    .submitLabel(.search)
                    .onSubmit { Task { await model.search(near: here) } }
                if model.busy { ProgressView() }
                if !model.query.isEmpty || model.destination != nil {
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

            if !model.results.isEmpty {
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
            }

            Text("© OpenStreetMap contributors · OpenMapTiles · OpenFreeMap")
                .font(.system(size: 9)).foregroundColor(.secondary)
        }
        .padding(14)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 6)
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
            if route.hasToll {
                Text(strings.tollRoad).font(.caption).bold().foregroundColor(.orange)
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 10)
        .frame(width: 160, alignment: .leading)
        .background(isSelected ? indigo.opacity(0.12) : Color(.secondarySystemBackground))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(isSelected ? indigo : Color.clear, lineWidth: 1.5))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

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
