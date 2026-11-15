import RoadPulseShared
import SwiftUI

/// Points, level, badges and the nickname shown on shared drives.
struct ProfileView: View {
    @ObservedObject var model: SearchModel
    let language: GuidanceLanguage
    let strings: AppStrings
    let onClose: () -> Void
    @State private var nickname = ""

    var body: some View {
        NavigationStack {
            Form {
                if !model.hasServer {
                    Text(strings.profileNeedsServer).foregroundColor(.secondary)
                } else if let p = model.profile {
                    summary(p)
                    Section(strings.nickname) {
                        TextField(strings.nicknameHint, text: $nickname)
                            .autocorrectionDisabled()
                        Button(strings.save) {
                            Task { await model.setNickname(nickname, language: language, strings: strings) }
                        }
                    }
                    Section {
                        ForEach(Array(p.badges.enumerated()), id: \.offset) { _, badge in
                            Label(badge.label, systemImage: badge.earned ? "rosette" : "lock")
                                .foregroundColor(badge.earned ? .primary : .secondary)
                        }
                    } header: {
                        Text(strings.badges)
                    } footer: {
                        Text(strings.howPointsWork)
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle(strings.profile)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(strings.done, action: onClose) }
            }
        }
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
        .task {
            await model.loadProfile(language: language)
            nickname = model.profile?.nickname ?? ""
        }
    }

    private func summary(_ p: ProfileCard) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 8) {
                Text(p.levelName).font(.title2).bold()
                Text(p.points).font(.headline)
                ProgressView(value: Double(p.progress))
                Text(p.toNext).font(.footnote).foregroundColor(.secondary)
            }
            .padding(.vertical, 4)
            HStack {
                stat(Int(p.reports), strings.statReports)
                stat(Int(p.confirmed), strings.statConfirmed)
                stat(Int(p.votes), strings.statVotes)
            }
        }
    }

    private func stat(_ value: Int, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text("\(value)").font(.title3).bold()
            Text(label).font(.caption).foregroundColor(.secondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }
}

/// The privacy policy (draft), in the app language.
struct PrivacyPolicyView: View {
    let strings: AppStrings

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ForEach(Array(strings.privacyPolicyText.enumerated()), id: \.offset) { _, paragraph in
                    Text(paragraph)
                }
            }
            .padding()
        }
        .navigationTitle(strings.privacyPolicy)
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }
}

/// The iOS share sheet (Messages, WhatsApp, Files…).
struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Something to share, for `.sheet(item:)`.
struct ShareItem: Identifiable {
    let id = UUID()
    let items: [Any]
}
