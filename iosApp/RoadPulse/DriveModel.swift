import CoreLocation
import RoadPulseShared
import SwiftUI

/// Turn-by-turn navigation on the iPhone: runs the shared drive session, speaks its prompts and
/// publishes its state for the driving screen.
@MainActor
final class DriveModel: ObservableObject {
    @Published var snapshot: DriveSnapshot?
    @Published var isDriving = false
    @Published var muted = false {
        didSet {
            speech.muted = muted
            if muted { speech.stop() }
        }
    }

    private let controller = DriveController(routingUrl: "https://valhalla1.openstreetmap.de")
    private let speech = Speech()
    private var voiceTag = "en-US"

    init() {
        controller.observe(
            onState: { [weak self] state in
                self?.snapshot = state
                self?.isDriving = state != nil
            },
            onSpeak: { [weak self] text in
                guard let self = self else { return }
                self.speech.speak(text, languageTag: self.voiceTag)
            },
            onArrived: {}
        )
    }

    /// Instructions follow the app language when the phone has a voice for it; otherwise English
    /// (spoken text and voice must match).
    func setLanguage(_ language: GuidanceLanguage) {
        if Speech.hasVoice(for: language.localeTag) {
            controller.language = language
            voiceTag = language.localeTag
        } else {
            controller.language = .en
            voiceTag = "en-US"
        }
    }

    func start(route: RouteSummary, to place: SearchResult, simulated: Bool) {
        if simulated {
            controller.startSimulated(route: route, destinationName: place.name, destLat: place.lat, destLon: place.lon, speedKmh: 60)
        } else {
            controller.start(route: route, destinationName: place.name, destLat: place.lat, destLon: place.lon)
        }
    }

    func stop() {
        speech.stop()
        controller.stop()
    }

    func onFix(_ location: CLLocation) {
        guard isDriving else { return }
        controller.onLocation(lat: location.coordinate.latitude, lon: location.coordinate.longitude,
                              accuracyM: location.horizontalAccuracy, bearingDeg: location.course, speedMps: location.speed)
    }
}

/// SF Symbol for a turn arrow name from the shared logic ("RIGHT", "ROUNDABOUT"…).
func turnSymbol(_ icon: String) -> String {
    switch icon {
    case "DEPART": return "location.north.fill"
    case "SLIGHT_RIGHT": return "arrow.up.right"
    case "RIGHT": return "arrow.turn.up.right"
    case "SHARP_RIGHT": return "arrow.turn.down.right"
    case "UTURN_RIGHT": return "arrow.uturn.right"
    case "UTURN_LEFT": return "arrow.uturn.left"
    case "SHARP_LEFT": return "arrow.turn.down.left"
    case "LEFT": return "arrow.turn.up.left"
    case "SLIGHT_LEFT": return "arrow.up.left"
    case "KEEP_RIGHT", "RAMP_RIGHT": return "arrow.up.right"
    case "KEEP_LEFT", "RAMP_LEFT": return "arrow.up.left"
    case "MERGE": return "arrow.triangle.merge"
    case "ROUNDABOUT": return "arrow.clockwise.circle"
    case "ARRIVE": return "flag.checkered"
    case "FERRY": return "ferry"
    default: return "arrow.up"
    }
}
