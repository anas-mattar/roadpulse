import SwiftUI
import RoadPulseShared

/// Step 2 of the iPhone app: search for a place and see the route choices, using the same shared
/// logic as Android (search, routing, route tags, texts in three languages). No map or GPS yet:
/// routes start from Kuala Lumpur city centre. The map and navigation come next.
@MainActor
final class SearchModel: ObservableObject {
    @Published var query = ""
    @Published var results: [SearchResult] = []
    @Published var selected: SearchResult?
    @Published var routes: [RouteSummary] = []
    @Published var busy = false
    @Published var errorText: String?

    // Public servers for now; the laptop's servers become a setting later (see docs/IOS.md).
    private let api = RoadPulseApi(geocodingUrl: "https://photon.komoot.io", routingUrl: "https://valhalla1.openstreetmap.de")
    private let startLat = 3.139
    private let startLon = 101.6869

    func search() async {
        let text = query.trimmingCharacters(in: .whitespaces)
        guard text.count >= 2 else { return }
        busy = true
        errorText = nil
        selected = nil
        routes = []
        do {
            results = try await api.search(query: text, nearLat: startLat, nearLon: startLon)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }

    func choose(_ place: SearchResult, language: GuidanceLanguage) async {
        selected = place
        busy = true
        errorText = nil
        do {
            routes = try await api.routes(fromLat: startLat, fromLon: startLon, toLat: place.lat, toLon: place.lon, language: language)
        } catch {
            errorText = error.localizedDescription
        }
        busy = false
    }
}

struct ContentView: View {
    @State private var language: GuidanceLanguage = .en
    @StateObject private var model = SearchModel()

    private var strings: AppStrings { StringsKt.stringsFor(language: language) }
    private var narrator: Narrator { NarrationKt.narratorFor(language: language) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker(strings.language, selection: $language) {
                        Text("English").tag(GuidanceLanguage.en)
                        Text("Bahasa Melayu").tag(GuidanceLanguage.ms)
                        Text("العربية").tag(GuidanceLanguage.ar)
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    HStack {
                        TextField(strings.whereTo, text: $model.query)
                            .submitLabel(.search)
                            .onSubmit { Task { await model.search() } }
                        if model.busy { ProgressView() }
                    }
                    if let error = model.errorText {
                        Text(error).foregroundColor(.red).font(.footnote)
                    }
                }

                if let place = model.selected {
                    Section(place.name) {
                        ForEach(Array(model.routes.enumerated()), id: \.offset) { _, route in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(route.tag).font(.caption).bold().foregroundColor(.indigo)
                                Text(strings.duration(minutes: route.minutes)).font(.title2).bold()
                                Text(strings.distance(km: route.km)).foregroundColor(.secondary)
                                if let via = route.via {
                                    Text(strings.via(road: via)).font(.footnote).foregroundColor(.secondary)
                                }
                                if route.hasToll {
                                    Text(strings.tollRoad).font(.footnote).foregroundColor(.orange)
                                }
                            }
                        }
                    }
                } else if !model.results.isEmpty {
                    Section {
                        ForEach(Array(model.results.enumerated()), id: \.offset) { _, place in
                            Button {
                                Task { await model.choose(place, language: language) }
                            } label: {
                                VStack(alignment: .leading) {
                                    Text(place.name).foregroundColor(.primary)
                                    if !place.detail.isEmpty {
                                        Text(place.detail).font(.footnote).foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }

                Section(strings.navigation) {
                    Text(narrator.arrived)
                    Text(narrator.schoolZoneAhead(meters: 300))
                    Text(narrator.fasterRouteFound(savedMinutes: 6))
                }
            }
            .navigationTitle("RoadPulse")
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }
}
