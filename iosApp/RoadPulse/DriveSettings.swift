import Foundation
import RoadPulseShared
import SwiftUI

/// Route, map and speed settings (kept on the phone), with the same choices as Android.
final class DriveSettings: ObservableObject {
    // Route
    @AppStorage("vehicle") var vehicle = "CAR" { willSet { objectWillChange.send() } }
    @AppStorage("avoidTolls") var avoidTolls = false { willSet { objectWillChange.send() } }
    @AppStorage("avoidHighways") var avoidHighways = false { willSet { objectWillChange.send() } }
    @AppStorage("avoidFerries") var avoidFerries = false { willSet { objectWillChange.send() } }
    @AppStorage("allowUnpaved") var allowUnpaved = false { willSet { objectWillChange.send() } }
    // Map
    @AppStorage("mapColors") var mapColors = "AUTO" { willSet { objectWillChange.send() } }
    @AppStorage("northUp") var northUp = false { willSet { objectWillChange.send() } }
    @AppStorage("showCompass") var showCompass = true { willSet { objectWillChange.send() } }
    @AppStorage("flatMap") var flatMap = false { willSet { objectWillChange.send() } }
    // Speed
    @AppStorage("showSpeedometer") var showSpeedometer = true { willSet { objectWillChange.send() } }
    @AppStorage("limitDisplay") var limitDisplay = "ALWAYS" { willSet { objectWillChange.send() } }
    @AppStorage("warnOverBy") var warnOverBy = 3 { willSet { objectWillChange.send() } }
    @AppStorage("speedingSound") var speedingSound = true { willSet { objectWillChange.send() } }

    func routeOptions() -> RouteOptions {
        Choices.shared.routeOptions(vehicleName: vehicle, avoidTolls: avoidTolls, avoidHighways: avoidHighways,
                                    avoidFerries: avoidFerries, allowUnpaved: allowUnpaved)
    }

    /// Changes when anything affecting the route or the speeding warning changes.
    var driveKey: String {
        "\(vehicle)|\(avoidTolls)|\(avoidHighways)|\(avoidFerries)|\(allowUnpaved)|\(warnOverBy)|\(speedingSound)"
    }
}
