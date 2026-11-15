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

    private let controller = DriveController(routingUrl: AppConfig.publicRouting)
    private let speech = Speech()
    private var voiceTag = "en-US"
    /// Name of the destination being driven to (for "I'm driving to …").
    private(set) var destinationName = ""

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

    /// Servers for rerouting, and for cameras, schools, reports and traffic (empty: none).
    func configure(routingUrl: String, backendUrl: String) {
        controller.routingUrl = routingUrl
        controller.backendUrl = backendUrl
    }

    /// Vehicle, roads to avoid and the speeding warning, from Settings.
    func applySettings(_ settings: DriveSettings) {
        controller.routeOptions = settings.routeOptions()
        controller.speedingMarginKmh = Int32(settings.warnOverBy)
        controller.speedingSound = settings.speedingSound
    }

    /// Adds a stop on the way; false when there is no position yet.
    func addStop(lat: Double, lon: Double) -> Bool { controller.addStop(lat: lat, lon: lon) }

    /// The route still ahead, for the overview.
    func remainingShape() -> [GeoPoint] { controller.remainingShape() }

    /// Anonymous road pieces for live traffic, only when the driver agreed.
    func setTrafficHelp(_ on: Bool) { controller.contributeTraffic = on }

    /// "Still there?" answer: true, false, or nil for Skip.
    func answerStillThere(_ answer: Bool?) {
        controller.answerStillThere(stillThere: answer.map { KotlinBoolean(bool: $0) })
    }

    /// Starts a live link for this drive and returns it.
    func startSharing(name: String?) async throws -> String {
        try await controller.startSharing(name: name)
    }

    func stopSharing() { controller.stopSharing() }

    func takeFasterRoute() { controller.takeFasterRoute() }
    func keepRoute() { controller.keepRoute() }

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
        destinationName = place.name
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
