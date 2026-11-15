import RoadPulseShared
import SwiftUI

/// The menu (profile, planned drives, settings, help); settings are grouped into pages.
extension ContentView {
    var settings: some View {
        MenuView(config: config, model: model, prefs: prefs, languageCode: $languageCode, muted: $drive.muted,
                 language: language, strings: strings,
                 onShare: { items in
                     showSettings = false
                     // Let the menu close before the share sheet opens.
                     DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { shareItem = ShareItem(items: items) }
                 },
                 onOpenPlace: { place in
                     showSettings = false
                     go(place)
                 },
                 onClose: { showSettings = false })
    }
}

struct MenuView: View {
    @ObservedObject var config: AppConfig
    @ObservedObject var model: SearchModel
    @ObservedObject var prefs: DriveSettings
    @Binding var languageCode: String
    @Binding var muted: Bool
    let language: GuidanceLanguage
    let strings: AppStrings
    let onShare: ([Any]) -> Void
    let onOpenPlace: (SearchResult) -> Void
    let onClose: () -> Void

    @State private var serverCheck: String?
    @State private var askTraffic = false
    @State private var askDelete = false
    @State private var note: String?

    var body: some View {
        NavigationStack {
            List {
                Section { header }
                Section {
                    if model.hasServer {
                        NavigationLink { ProfileView(model: model, language: language, strings: strings) } label: {
                            Label(strings.profile, systemImage: "person.crop.circle")
                        }
                    }
                    NavigationLink { plannedPage } label: { Label(strings.planDrive, systemImage: "alarm") }
                    NavigationLink { settingsPage } label: { Label(strings.settings, systemImage: "gearshape") }
                    NavigationLink { helpPage } label: { Label(strings.helpAndFeedback, systemImage: "questionmark.circle") }
                }
            }
            .navigationTitle(strings.menu)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(strings.done, action: onClose) }
            }
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
        .task { await model.loadProfile(language: language) }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: "person.crop.circle.fill").font(.system(size: 44)).foregroundColor(.accentColor)
            VStack(alignment: .leading, spacing: 2) {
                let name = model.profile?.nickname ?? ""
                Text(name.isEmpty ? "RoadPulse" : name).font(.title3).bold()
                if let p = model.profile {
                    Text(p.levelName + " · " + p.points).font(.subheadline).foregroundColor(.secondary)
                }
            }
        }
        .padding(.vertical, 4)
    }

    // MARK: Planned drives

    private var plannedPage: some View {
        List {
            Section {
                ForEach(Array(model.plannedDrives().enumerated()), id: \.offset) { _, item in
                    Button { onOpenPlace(item.place) } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.place.name).foregroundColor(.primary)
                            Text(plannedLine(item)).font(.footnote).foregroundColor(.secondary)
                        }
                    }
                    .swipeActions {
                        Button(strings.remove(name: item.place.name), role: .destructive) { model.removePlanned(item) }
                    }
                }
            } footer: {
                Text(Choices.shared.helpTopics(language: language).dropFirst(2).first?[1] ?? "")
            }
        }
        .navigationTitle(strings.plannedDrives)
    }

    private func plannedLine(_ item: PlannedItem) -> String {
        let tomorrow = Calendar.current.isDateInTomorrow(date(item.arriveByMillis))
        return strings.plannedFor(time: clock(item.arriveByMillis), tomorrow: tomorrow) + " · "
            + strings.leaveAt(time: clock(item.leaveAtMillis), driveMinutes: item.driveMinutes, spareMinutes: model.book.spareMinutes)
    }

    // MARK: Help

    private var helpPage: some View {
        List {
            ForEach(Choices.shared.helpTopics(language: language), id: \.self) { topic in
                VStack(alignment: .leading, spacing: 4) {
                    Text(topic[0]).font(.headline)
                    Text(topic[1]).font(.subheadline).foregroundColor(.secondary)
                }
                .padding(.vertical, 2)
            }
            Section {
                if let mail = URL(string: "mailto:?subject=RoadPulse%20feedback") {
                    Link(strings.sendFeedback, destination: mail)
                }
                Text(strings.version(v: appVersion)).font(.footnote).foregroundColor(.secondary)
            }
        }
        .navigationTitle(strings.helpAndFeedback)
    }

    private var appVersion: String {
        (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "?"
    }

    // MARK: Settings pages

    private var settingsPage: some View {
        List {
            page(strings.general, icon: "gearshape") { generalPage }
            page(strings.mapDisplay, icon: "map") { mapPage }
            page(strings.voiceAndSound, icon: "speaker.wave.2") { voicePage }
            page(strings.navigationSettings, icon: "arrow.triangle.turn.up.right.diamond") { navigationPage }
            page(strings.vehicleDetails, icon: "car") { vehiclePage }
            page(strings.alertsAndReports, icon: "exclamationmark.triangle") { alertsPage }
            page(strings.speedometer, icon: "speedometer") { speedPage }
            page(strings.privacy, icon: "lock") { privacyPage }
            page("Server", icon: "server.rack") { serverPage }
        }
        .navigationTitle(strings.settings)
    }

    private func page<Content: View>(_ title: String, icon: String, @ViewBuilder content: @escaping () -> Content) -> some View {
        NavigationLink {
            content().navigationTitle(title)
        } label: {
            Label(title, systemImage: icon)
        }
    }

    private var generalPage: some View {
        Form {
            Picker(strings.language, selection: $languageCode) {
                Text("English").tag("en")
                Text("Bahasa Melayu").tag("ms")
                Text("العربية").tag("ar")
            }
            .pickerStyle(.inline)
        }
    }

    private var mapPage: some View {
        Form {
            Picker(strings.mapColours, selection: $prefs.mapColors) {
                ForEach(Choices.shared.mapColors(language: language), id: \.self) { c in Text(c[1]).tag(c[0]) }
            }
            .pickerStyle(.inline)
            Section(strings.mapView) {
                Toggle(strings.northUp, isOn: $prefs.northUp)
                Toggle(strings.showCompass, isOn: $prefs.showCompass)
                Toggle(strings.flatMap, isOn: $prefs.flatMap)
            }
        }
    }

    private var voicePage: some View {
        Form {
            Toggle(strings.muteVoiceSetting, isOn: $muted)
        }
    }

    private var navigationPage: some View {
        Form {
            Toggle(strings.avoidTolls, isOn: $prefs.avoidTolls)
            Toggle(strings.avoidHighways, isOn: $prefs.avoidHighways)
            Toggle(strings.avoidFerries, isOn: $prefs.avoidFerries)
            Toggle(strings.allowUnpaved, isOn: $prefs.allowUnpaved)
        }
    }

    private var vehiclePage: some View {
        Form {
            Picker(strings.vehicleDetails, selection: $prefs.vehicle) {
                ForEach(Choices.shared.vehicles(language: language), id: \.self) { v in Text(v[1]).tag(v[0]) }
            }
            .pickerStyle(.inline)
        }
    }

    private var alertsPage: some View {
        Form {
            Section {
                if model.hasServer {
                    Toggle(strings.helpTraffic, isOn: trafficToggle)
                }
            } footer: {
                Text(strings.helpTrafficText)
            }
        }
        .alert(strings.trafficConsentTitle, isPresented: $askTraffic) {
            Button(strings.allow) { config.setHelpTraffic(true) }
            Button(strings.notNow, role: .cancel) {}
        } message: {
            Text(strings.helpTrafficText)
        }
    }

    private var speedPage: some View {
        Form {
            Toggle(strings.showSpeedometer, isOn: $prefs.showSpeedometer)
            Picker(strings.showSpeedLimit, selection: $prefs.limitDisplay) {
                ForEach(Choices.shared.limitDisplays(language: language), id: \.self) { c in Text(c[1]).tag(c[0]) }
            }
            Stepper("\(strings.warnOverLimitBy): \(prefs.warnOverBy) \(strings.kmh)", value: $prefs.warnOverBy, in: 0...20)
            Toggle(strings.soundWhenSpeeding, isOn: $prefs.speedingSound)
        }
    }

    private var serverPage: some View {
        Form { server }
    }

    private var privacyPage: some View {
        Form { privacy }
            .confirmationDialog(strings.deleteDataConfirm, isPresented: $askDelete, titleVisibility: .visible) {
                Button(strings.deleteData, role: .destructive) { deleteServerData() }
                Button(strings.cancel, role: .cancel) {}
            }
    }

    // Developer setting (English, like on Android): the RoadPulse servers on the laptop.
    private var server: some View {
        Section {
            Toggle("Use my laptop's server", isOn: $config.useLaptop)
            if config.useLaptop {
                TextField("Laptop address, e.g. 192.168.1.20", text: $config.laptopAddress)
                    .keyboardType(.numbersAndPunctuation)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                Toggle("Maps from my laptop", isOn: $config.laptopMaps)
                Button("Test connection") {
                    if config.laptopAddress.trimmingCharacters(in: .whitespaces).isEmpty {
                        serverCheck = "Type your laptop's address first (open-iphone-access.ps1 prints it, e.g. 192.168.1.20)."
                        return
                    }
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

    private var trafficToggle: Binding<Bool> {
        Binding(get: { config.helpTraffic }, set: { on in
            if on { askTraffic = true } else { config.setHelpTraffic(false) }
        })
    }

    private var privacy: some View {
        Section {
            Text(strings.privacyText).font(.footnote).foregroundColor(.secondary)
            NavigationLink(strings.privacyPolicy) { PrivacyPolicyView(strings: strings) }
            if model.hasServer {
                Button(strings.exportData) { exportServerData() }
                Button(strings.deleteData, role: .destructive) { askDelete = true }
            }
            Button(strings.clearSavedPlaces, role: .destructive) {
                model.clearSavedPlaces(strings: strings)
                note = strings.savedPlacesCleared
            }
            if let note = note { Text(note).font(.footnote).foregroundColor(.secondary) }
        } header: {
            Text(strings.privacy)
        }
    }

    /// A copy of everything the server holds about this phone, as a JSON file to save or send.
    private func exportServerData() {
        Task {
            do {
                let json = try await model.api.exportMyData()
                let file = FileManager.default.temporaryDirectory.appendingPathComponent("roadpulse-my-data.json")
                try json.write(to: file, atomically: true, encoding: .utf8)
                onShare([file])
            } catch {
                note = strings.exportFailed
            }
        }
    }

    private func deleteServerData() {
        Task {
            do {
                try await model.api.deleteMyData()
                config.setHelpTraffic(false)
                model.profile = nil
                note = strings.dataDeleted
            } catch {
                note = strings.deleteFailed
            }
        }
    }
}
