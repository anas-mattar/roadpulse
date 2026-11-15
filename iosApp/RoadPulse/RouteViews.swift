import RoadPulseShared
import SwiftUI

/// The route as a thin bar: slow stretches amber, jams red; [progress] marks how far the car has come.
struct TrafficBarView: View {
    let pieces: [TrafficPiece]
    var progress: Double? = nil
    var track: Color = Color(.systemGray5)

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(track).frame(height: 6)
                if let p = progress {
                    Capsule().fill(Color(red: 0.36, green: 0.79, blue: 0.65)).frame(width: max(6, w * CGFloat(p)), height: 6)
                }
                ForEach(Array(pieces.enumerated()), id: \.offset) { _, piece in
                    Capsule()
                        .fill(piece.level == "JAM" ? Color(hex: "#D20F39") : Color(hex: "#F59F00"))
                        .frame(width: max(4, w * CGFloat(piece.to - piece.from)), height: 6)
                        .offset(x: w * CGFloat(piece.from))
                }
                if let p = progress {
                    Circle().fill(Color.white).frame(width: 10, height: 10).offset(x: max(0, w * CGFloat(p) - 5))
                }
            }
            .frame(height: 10)
        }
        .frame(height: 10)
        .environment(\.layoutDirection, .leftToRight)
    }
}

/// Map / List switch above the route choices.
struct MapListSwitch: View {
    @Binding var list: Bool
    let strings: AppStrings
    let indigo: Color

    var body: some View {
        HStack(spacing: 0) {
            option(strings.mapTab, isList: false)
            option(strings.listTab, isList: true)
        }
        .padding(4)
        .background(Color(.systemBackground))
        .clipShape(Capsule())
        .shadow(radius: 4)
    }

    private func option(_ label: String, isList: Bool) -> some View {
        let on = list == isList
        return Button { list = isList } label: {
            Text(label).font(.subheadline.weight(on ? .semibold : .regular))
                .padding(.horizontal, 18).padding(.vertical, 8)
                .background(on ? indigo.opacity(0.15) : Color.clear)
                .foregroundColor(on ? indigo : .secondary)
                .clipShape(Capsule())
        }
    }
}

/// One large card per route choice: time with traffic, arrival, km, via, toll, traffic bar and Go.
struct RouteListCard: View {
    let route: RouteSummary
    let selected: Bool
    let strings: AppStrings
    let indigo: Color
    let onSelect: () -> Void
    let onGo: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(strings.duration(minutes: route.minutesWithTraffic)).font(.title2).bold()
                    Text(arrivalText + " · " + strings.distance(km: route.km)).font(.subheadline.weight(.medium))
                }
                Spacer()
                Button(action: onGo) {
                    Label(strings.goShort, systemImage: "location.north.fill").font(.headline)
                        .padding(.horizontal, 18).padding(.vertical, 10)
                        .background(indigo).foregroundColor(.white).clipShape(Capsule())
                }
            }
            if let via = route.via {
                Text(strings.via(road: via)).font(.subheadline).foregroundColor(.secondary).lineLimit(1)
            }
            if let toll = route.tollLabel {
                Text(toll).font(.caption.weight(.semibold)).foregroundColor(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(Color(hex: "#4C4F69")).clipShape(RoundedRectangle(cornerRadius: 6))
            }
            TrafficBarView(pieces: route.trafficPieces).padding(.top, 6)
        }
        .padding(16)
        .background(Color(.systemBackground))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(selected ? indigo : Color.clear, lineWidth: 2))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .shadow(radius: 5)
        .onTapGesture(perform: onSelect)
    }

    private var arrivalText: String {
        Date().addingTimeInterval(Double(route.minutesWithTraffic) * 60).formatted(date: .omitted, time: .shortened)
    }
}
