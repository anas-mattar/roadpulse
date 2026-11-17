import RoadPulseShared
import SwiftUI

/// "Report a problem with this place": what is wrong with a place from search, whether it is
/// where the driver is now, and a note. A moderator checks each report before anything changes.
struct PlaceProblemSheet: View {
    let api: RoadPulseApi
    let language: GuidanceLanguage
    let strings: AppStrings
    let placeName: String
    let onSend: (_ kind: String, _ useMyPosition: Bool, _ note: String) -> Void
    let onCancel: () -> Void

    @State private var kind = ""
    @State private var useMyPosition = false
    @State private var note = ""

    private var moving: Bool { kind == "WRONG_POSITION" }

    private var canSend: Bool {
        api.canSendPlaceProblem(kindName: kind, hasPosition: moving && useMyPosition, note: note)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(placeName).font(.headline)
                }
                Section(strings.whatIsWrong) {
                    Picker(strings.whatIsWrong, selection: $kind) {
                        ForEach(api.placeProblemKinds(language: language), id: \.self) { k in Text(k[1]).tag(k[0]) }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                }
                if moving {
                    Section {
                        Toggle(strings.placeIsWhereIAm, isOn: $useMyPosition)
                    }
                }
                Section {
                    TextField(moving ? strings.placeProblemNoteHint : strings.placeNoteHint, text: $note, axis: .vertical)
                        .lineLimit(1...3)
                }
            }
            .navigationTitle(strings.reportPlaceProblem)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(strings.cancel, action: onCancel) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(strings.send) {
                        onSend(kind, moving && useMyPosition, String(note.trimmingCharacters(in: .whitespaces).prefix(200)))
                    }
                    .disabled(!canSend)
                }
            }
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }
}
