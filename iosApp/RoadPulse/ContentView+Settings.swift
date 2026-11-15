import RoadPulseShared
import SwiftUI

/// Settings: language, the laptop server, and privacy.
extension ContentView {
    var settings: some View {
        SettingsView(config: config, model: model, languageCode: $languageCode, strings: strings,
                     onShare: { items in
                         showSettings = false
                         // Let the settings sheet close before the share sheet opens.
                         DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { shareItem = ShareItem(items: items) }
                     },
                     onClose: { showSettings = false })
    }
}

struct SettingsView: View {
    @ObservedObject var config: AppConfig
    @ObservedObject var model: SearchModel
    @Binding var languageCode: String
    let strings: AppStrings
    let onShare: ([Any]) -> Void
    let onClose: () -> Void

    @State private var serverCheck: String?
    @State private var askTraffic = false
    @State private var askDelete = false
    @State private var note: String?

    var body: some View {
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
                server
                privacy
            }
            .navigationTitle(strings.settings)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(strings.done, action: onClose) }
            }
            .alert(strings.trafficConsentTitle, isPresented: $askTraffic) {
                Button(strings.allow) { config.setHelpTraffic(true) }
                Button(strings.notNow, role: .cancel) {}
            } message: {
                Text(strings.helpTrafficText)
            }
            .confirmationDialog(strings.deleteDataConfirm, isPresented: $askDelete, titleVisibility: .visible) {
                Button(strings.delete, role: .destructive) { deleteServerData() }
                Button(strings.cancel, role: .cancel) {}
            }
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
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
            if model.hasServer {
                Toggle(strings.helpTraffic, isOn: trafficToggle)
            }
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
