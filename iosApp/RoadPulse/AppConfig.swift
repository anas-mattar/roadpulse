import Foundation
import RoadPulseShared
import SwiftUI

/// Which servers the iPhone app uses. By default the public routing and search servers; with
/// "Use my laptop's server" on, the RoadPulse servers on the laptop (same Wi-Fi, see docs/IOS.md).
final class AppConfig: ObservableObject {
    @AppStorage("useLaptop") var useLaptop = false { willSet { objectWillChange.send() } }
    @AppStorage("laptopAddress") var laptopAddress = "" { willSet { objectWillChange.send() } }
    @AppStorage("laptopMaps") var laptopMaps = false { willSet { objectWillChange.send() } }
    /// "Help with live traffic" (anonymous road pieces while driving, ADR 0004). Off until the driver agrees.
    @AppStorage("helpTraffic") var helpTraffic = false { willSet { objectWillChange.send() } }

    /// Turns traffic help on or off, keeping when and to which wording the driver agreed (on the phone only).
    func setHelpTraffic(_ on: Bool) {
        helpTraffic = on
        if on {
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "trafficConsentAt")
            UserDefaults.standard.set("2026-11-15", forKey: "trafficConsentVersion")
        }
    }

    static let publicRouting = "https://valhalla1.openstreetmap.de"
    static let search = "https://photon.komoot.io"
    static let publicStyle = "https://tiles.openfreemap.org/styles/liberty"

    private var host: String? {
        let a = laptopAddress.trimmingCharacters(in: .whitespaces)
        return useLaptop && !a.isEmpty ? a : nil
    }

    var routingUrl: String { host.map { "http://\($0):8002" } ?? AppConfig.publicRouting }
    /// Empty without the laptop: reports, cameras, places, tolls and traffic are then off.
    var backendUrl: String { host.map { "http://\($0):8080" } ?? "" }
    /// Day map, or the night map ("fiord") after sunset or when chosen in settings.
    func styleUrl(dark: Bool) -> String {
        let name = dark ? "fiord" : "liberty"
        if let h = host, laptopMaps { return "http://\(h):8090/styles/\(name)/style.json" }
        return "https://tiles.openfreemap.org/styles/\(name)"
    }

    /// Changes whenever any server address changes (to rebuild the clients).
    var key: String { "\(routingUrl)|\(backendUrl)|\(styleUrl(dark: false))" }

    func makeApi() -> RoadPulseApi {
        RoadPulseApi(geocodingUrl: AppConfig.search, routingUrl: routingUrl, backendUrl: backendUrl)
    }
}

extension Color {
    /// "#1E66F5" → colour.
    init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&value)
        self.init(red: Double((value >> 16) & 0xFF) / 255, green: Double((value >> 8) & 0xFF) / 255, blue: Double(value & 0xFF) / 255)
    }
}

extension UIColor {
    convenience init(hex: String) {
        var value: UInt64 = 0
        Scanner(string: hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))).scanHexInt64(&value)
        self.init(red: CGFloat((value >> 16) & 0xFF) / 255, green: CGFloat((value >> 8) & 0xFF) / 255,
                  blue: CGFloat(value & 0xFF) / 255, alpha: 1)
    }
}

/// SF Symbol for a report type name.
func reportSymbol(_ type: String) -> String {
    switch type {
    case "POLICE": return "shield.fill"
    case "CRASH": return "exclamationmark.octagon.fill"
    case "HAZARD": return "exclamationmark.triangle.fill"
    case "TRAFFIC_JAM": return "car.2.fill"
    case "ROAD_CLOSED": return "nosign"
    default: return "mappin"
    }
}
