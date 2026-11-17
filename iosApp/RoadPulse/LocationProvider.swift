import CoreLocation
import Foundation
import RoadPulseShared

/// The phone's position (asks for "While Using the App" permission on first use).
final class LocationProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    /// Every GPS fix, for the drive in progress.
    var onFix: ((CLLocation) -> Void)?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
    }

    func start() {
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    /// While navigating: best accuracy, and keep receiving fixes with the screen off.
    func setNavigating(_ navigating: Bool) {
        manager.desiredAccuracy = navigating ? kCLLocationAccuracyBestForNavigation : kCLLocationAccuracyBest
        manager.activityType = navigating ? .automotiveNavigation : .other
        manager.pausesLocationUpdatesAutomatically = !navigating
        manager.allowsBackgroundLocationUpdates = navigating
        manager.showsBackgroundLocationIndicator = navigating
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let last = locations.last else { return }
        coordinate = last.coordinate
        // Which way the phone is travelling, so a new route starts in that direction.
        HeadingFeed.shared.update(lat: last.coordinate.latitude, lon: last.coordinate.longitude, bearingDeg: last.course, speedMps: last.speed)
        onFix?(last)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // No fix yet (indoors, permission not given): routes start from the city centre meanwhile.
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.startUpdatingLocation()
        }
    }
}
