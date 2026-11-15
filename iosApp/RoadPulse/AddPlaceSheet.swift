import RoadPulseShared
import SwiftUI

/// "Add a missing place": name, kind and an optional note for a place search does not know.
struct AddPlaceSheet: View {
    let api: RoadPulseApi
    let language: GuidanceLanguage
    let strings: AppStrings
    let initialName: String
    let onAdd: (_ name: String, _ kind: String, _ note: String) -> Void
    let onCancel: () -> Void

    @State private var name = ""
    @State private var kind = ""
    @State private var note = ""

    private var canSave: Bool {
        name.filter { $0.isLetter || $0.isNumber }.count >= 2 && !kind.isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(strings.placeName, text: $name)
                        .textInputAutocapitalization(.words)
                }
                Section(strings.whatKindOfPlace) {
                    Picker(strings.whatKindOfPlace, selection: $kind) {
                        ForEach(api.placeKinds(language: language), id: \.self) { k in Text(k[1]).tag(k[0]) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                Section {
                    TextField(strings.placeNoteHint, text: $note, axis: .vertical)
                        .lineLimit(1...3)
                }
            }
            .navigationTitle(strings.addPlace)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(strings.cancel, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(strings.save) {
                        onAdd(String(name.trimmingCharacters(in: .whitespaces).prefix(80)), kind,
                              String(note.trimmingCharacters(in: .whitespaces).prefix(120)))
                    }
                    .disabled(!canSave)
                }
            }
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
        .onAppear { if name.isEmpty { name = initialName } }
    }
}
