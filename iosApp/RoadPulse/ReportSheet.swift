import RoadPulseShared
import SwiftUI

/// Report in two taps: the type, then optional details (and how long, for a closure). Sent at the
/// current position.
struct ReportSheet: View {
    let api: RoadPulseApi
    let language: GuidanceLanguage
    let strings: AppStrings
    let onSend: (_ type: String, _ subtype: String?, _ durationMinutes: Int32) -> Void
    let onCancel: () -> Void

    /// The chosen type: (name, label, colour).
    @State private var type: [String]?
    @State private var subtype: String?
    @State private var duration = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let t = type {
                details(t)
            } else {
                types
            }
        }
        .padding(20)
        .presentationDetents([.medium, .large])
        .environment(\.layoutDirection, strings.rtl ? .rightToLeft : .leftToRight)
    }

    private var types: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(strings.report).font(.title2).bold()
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(api.reportTypes(language: language), id: \.self) { t in
                    Button {
                        subtype = nil
                        duration = ""
                        type = t
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
            Button(strings.cancel, action: onCancel).frame(maxWidth: .infinity)
        }
    }

    private func details(_ t: [String]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: reportSymbol(t[0]))
                    .foregroundColor(.white).frame(width: 40, height: 40)
                    .background(Color(hex: t[2])).clipShape(Circle())
                Text(t[1]).font(.title2).bold()
            }
            chips(api.reportDetails(typeName: t[0], language: language), selected: subtype ?? "") { code in
                subtype = subtype == code ? nil : code
            }
            if t[0] == "ROAD_CLOSED" {
                Text(strings.howLong).font(.headline)
                chips(api.closureDurations(language: language), selected: duration) { minutes in duration = minutes }
            }
            Spacer(minLength: 0)
            HStack(spacing: 10) {
                Button { type = nil } label: {
                    Text(strings.back).frame(maxWidth: .infinity).frame(height: 48)
                        .overlay(Capsule().stroke(Color.secondary, lineWidth: 1)).foregroundColor(.primary)
                }
                Button { onSend(t[0], subtype, Int32(duration) ?? -1) } label: {
                    Text(strings.send).bold().frame(maxWidth: .infinity).frame(height: 48)
                        .background(Color(hex: t[2])).foregroundColor(.white).clipShape(Capsule())
                }
            }
        }
    }

    /// Choices as wrapping chips: each item is (value, label).
    private func chips(_ items: [[String]], selected: String, onTap: @escaping (String) -> Void) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 110), spacing: 8)], alignment: .leading, spacing: 8) {
            ForEach(items, id: \.self) { item in
                let on = item[0] == selected
                Button { onTap(item[0]) } label: {
                    Text(item[1]).font(.subheadline)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(on ? Color.accentColor : Color(.secondarySystemBackground))
                        .foregroundColor(on ? .white : .primary)
                        .clipShape(Capsule())
                }
            }
        }
    }
}
