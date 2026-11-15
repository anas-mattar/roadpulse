import MapLibre
import RoadPulseShared
import SwiftUI

/// The map: your position, the route choices (selected one in indigo, others grey) and the destination.
struct MapView: UIViewRepresentable {
    var routes: [RouteSummary]
    var selected: Int
    var destination: SearchResult?
    /// Increases each time the driver taps "my location".
    var recenterToken: Int
    /// The drive in progress: the map then shows its route and follows the car.
    var drive: DriveSnapshot? = nil

    static let styleURL = URL(string: "https://tiles.openfreemap.org/styles/liberty")
    static let kualaLumpur = CLLocationCoordinate2D(latitude: 3.139, longitude: 101.6869)

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: MapView.styleURL)
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        map.showsUserLocation = true
        map.setCenter(MapView.kualaLumpur, zoomLevel: 11, animated: false)
        map.delegate = context.coordinator
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.apply(self, to: map)
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        private var styleLoaded = false
        private var pending: MapView?
        private var fittedKey = ""
        private var lastRecenter = 0
        private var destinationPin: MLNPointAnnotation?
        private var carPin: MLNPointAnnotation?
        private var drawnKey = ""
        private var following = true

        /// The driver moved the map by hand: stop following until "my location" is tapped.
        func mapView(_ mapView: MLNMapView, regionWillChangeWith reason: MLNCameraChangeReason, animated: Bool) {
            if reason.contains(.gesturePan) || reason.contains(.gesturePinch) || reason.contains(.gestureRotate) {
                following = false
            }
        }

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            styleLoaded = true
            if let view = pending { apply(view, to: mapView) }
        }

        func apply(_ view: MapView, to map: MLNMapView) {
            pending = view
            if view.recenterToken != lastRecenter {
                lastRecenter = view.recenterToken
                following = true
                if view.drive == nil { map.userTrackingMode = .follow }
            }
            guard styleLoaded, let style = map.style else { return }

            if let drive = view.drive {
                applyDrive(drive, to: map, style: style)
                return
            }
            if let pin = carPin {
                map.removeAnnotation(pin)
                carPin = nil
                fittedKey = ""
            }
            // Redraw the choices only when they change.
            let choicesKey = "preview|\(view.selected)|" + view.routes.map { "\($0.km)-\($0.minutes)" }.joined(separator: "|")
                + "|\(view.destination?.name ?? "")"
            if choicesKey == drawnKey { return }
            drawnKey = choicesKey

            // Route lines: remove the old ones, then draw the others first and the selected on top.
            for id in ["route-main", "route-alt"] {
                if let layer = style.layer(withIdentifier: id) { style.removeLayer(layer) }
                if let source = style.source(withIdentifier: id) { style.removeSource(source) }
            }
            var others: [MLNPolylineFeature] = []
            var main: [MLNPolylineFeature] = []
            for (index, route) in view.routes.enumerated() {
                var coordinates = route.shape.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
                let line = MLNPolylineFeature(coordinates: &coordinates, count: UInt(coordinates.count))
                if index == view.selected { main.append(line) } else { others.append(line) }
            }
            addLine(to: style, id: "route-alt", lines: others, color: UIColor(white: 0.62, alpha: 1), width: 6)
            addLine(to: style, id: "route-main", lines: main, color: UIColor(red: 0.33, green: 0.29, blue: 0.72, alpha: 1), width: 7)

            // Destination pin.
            if let pin = destinationPin { map.removeAnnotation(pin) }
            destinationPin = nil
            if let place = view.destination {
                let pin = MLNPointAnnotation()
                pin.coordinate = CLLocationCoordinate2D(latitude: place.lat, longitude: place.lon)
                pin.title = place.name
                map.addAnnotation(pin)
                destinationPin = pin
            }

            // Fit all route choices on screen once, when they arrive (not on every selection).
            let key = view.routes.map { "\($0.km)-\($0.minutes)" }.joined(separator: "|")
            if key != fittedKey {
                fittedKey = key
                let all = view.routes.flatMap { $0.shape }
                if let first = all.first {
                    var south = first.lat, north = first.lat, west = first.lon, east = first.lon
                    for p in all {
                        south = min(south, p.lat); north = max(north, p.lat)
                        west = min(west, p.lon); east = max(east, p.lon)
                    }
                    let bounds = MLNCoordinateBounds(
                        sw: CLLocationCoordinate2D(latitude: south, longitude: west),
                        ne: CLLocationCoordinate2D(latitude: north, longitude: east))
                    map.setVisibleCoordinateBounds(bounds, edgePadding: UIEdgeInsets(top: 90, left: 40, bottom: 380, right: 40),
                                                   animated: true, completionHandler: nil)
                }
            }
        }

        /// While driving: the current route (it changes on a reroute), the car, and a camera that follows it.
        private func applyDrive(_ drive: DriveSnapshot, to map: MLNMapView, style: MLNStyle) {
            let key = "drive|\(drive.routeShape.count)|\(drive.routeShape.first?.lat ?? 0)|\(drive.routeShape.last?.lon ?? 0)"
            if key != drawnKey {
                drawnKey = key
                for id in ["route-main", "route-alt"] {
                    if let layer = style.layer(withIdentifier: id) { style.removeLayer(layer) }
                    if let source = style.source(withIdentifier: id) { style.removeSource(source) }
                }
                var coordinates = drive.routeShape.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
                let line = MLNPolylineFeature(coordinates: &coordinates, count: UInt(coordinates.count))
                addLine(to: style, id: "route-main", lines: [line], color: UIColor(red: 0.33, green: 0.29, blue: 0.72, alpha: 1), width: 8)
                following = true
            }
            guard drive.hasPosition else { return }
            let position = CLLocationCoordinate2D(latitude: drive.lat, longitude: drive.lon)
            if let pin = carPin {
                pin.coordinate = position
            } else {
                let pin = MLNPointAnnotation()
                pin.coordinate = position
                map.addAnnotation(pin)
                carPin = pin
            }
            if following {
                let direction = drive.bearingDeg >= 0 ? drive.bearingDeg : map.direction
                map.setCenter(position, zoomLevel: 16.5, direction: direction, animated: true)
            }
        }

        private func addLine(to style: MLNStyle, id: String, lines: [MLNPolylineFeature], color: UIColor, width: Double) {
            guard !lines.isEmpty else { return }
            let source = MLNShapeSource(identifier: id, features: lines, options: nil)
            style.addSource(source)
            let layer = MLNLineStyleLayer(identifier: id, source: source)
            layer.lineColor = NSExpression(forConstantValue: color)
            layer.lineWidth = NSExpression(forConstantValue: width)
            layer.lineCap = NSExpression(forConstantValue: "round")
            layer.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(layer)
        }
    }
}
