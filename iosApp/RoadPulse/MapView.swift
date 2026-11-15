import MapLibre
import RoadPulseShared
import SwiftUI

/// A community report shown on the map.
final class ReportAnnotation: MLNPointAnnotation {
    var reportId = ""
    var typeName = ""
    var colorHex = "#4C4F69"
}

/// A stop, the parked car or a long-pressed point: a round marker with a symbol.
final class MarkerAnnotation: MLNPointAnnotation {
    var symbol = "mappin"
    var colorHex = "#5549B8"
}

/// The map: your position, route choices (selected in indigo, others grey) or the route being
/// driven, the destination, community reports and live traffic (orange slow, red jammed).
struct MapView: UIViewRepresentable {
    var styleUrl: String
    var routes: [RouteSummary]
    var selected: Int
    var destination: SearchResult?
    /// Increases each time the driver taps "my location".
    var recenterToken: Int
    /// The drive in progress: the map then shows its route and follows the car.
    var drive: DriveSnapshot? = nil
    var reports: [ReportPin] = []
    var traffic: [TrafficPolyline] = []
    /// The visible area changed (south, west, north, east).
    var onArea: (Double, Double, Double, Double) -> Void = { _, _, _, _ in }
    var onReportTap: (String) -> Void = { _ in }
    /// Map view settings: north always up, compass, tilted map while driving.
    var northUp = false
    var showCompass = true
    var tilted = true
    var stops: [SearchResult] = []
    var parking: SearchResult? = nil
    var pinned: SearchResult? = nil
    /// Increases each time the driver taps "overview": show [overviewShape] (the route ahead).
    var overviewToken = 0
    var overviewShape: [GeoPoint] = []
    var onLongPress: (Double, Double) -> Void = { _, _ in }

    static let kualaLumpur = CLLocationCoordinate2D(latitude: 3.139, longitude: 101.6869)
    static let indigo = UIColor(red: 0.33, green: 0.29, blue: 0.72, alpha: 1)

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: URL(string: styleUrl))
        map.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        map.showsUserLocation = true
        map.setCenter(MapView.kualaLumpur, zoomLevel: 11, animated: false)
        map.delegate = context.coordinator
        context.coordinator.loadedStyleUrl = styleUrl
        // Long press: actions for that point (drive here, add a stop, parked here, save).
        let press = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.longPressed(_:)))
        for other in map.gestureRecognizers ?? [] where other is UILongPressGestureRecognizer {
            press.require(toFail: other)
        }
        map.addGestureRecognizer(press)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.apply(self, to: map)
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var loadedStyleUrl = ""
        private var styleLoaded = false
        private var view: MapView?
        private var fittedKey = ""
        private var lastRecenter = 0
        private var destinationPin: MLNPointAnnotation?
        private var carPin: MLNPointAnnotation?
        private var reportPins: [String: ReportAnnotation] = [:]
        private var drawnKey = ""
        private var trafficKey = ""
        private var following = true
        private var lastOverview = 0
        private var markers: [MarkerAnnotation] = []
        private var markersKey = ""

        @objc func longPressed(_ press: UILongPressGestureRecognizer) {
            guard press.state == .began, let map = press.view as? MLNMapView else { return }
            let point = map.convert(press.location(in: map), toCoordinateFrom: map)
            view?.onLongPress(point.latitude, point.longitude)
        }

        // MARK: Map events

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            styleLoaded = true
            drawnKey = ""     // a new style has none of our layers
            trafficKey = ""
            if let view = view { apply(view, to: mapView) }
        }

        /// The driver moved the map by hand: stop following until "my location" is tapped.
        func mapView(_ mapView: MLNMapView, regionWillChangeWith reason: MLNCameraChangeReason, animated: Bool) {
            if reason.contains(.gesturePan) || reason.contains(.gesturePinch) || reason.contains(.gestureRotate) {
                following = false
            }
        }

        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
            let b = mapView.visibleCoordinateBounds
            view?.onArea(b.sw.latitude, b.sw.longitude, b.ne.latitude, b.ne.longitude)
        }

        func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool { false }

        func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
            if let report = annotation as? ReportAnnotation { view?.onReportTap(report.reportId) }
            mapView.deselectAnnotation(annotation, animated: false)
        }

        func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? {
            if let marker = annotation as? MarkerAnnotation {
                let id = "marker-\(marker.symbol)-\(marker.colorHex)"
                if let reused = mapView.dequeueReusableAnnotationImage(withIdentifier: id) { return reused }
                return MLNAnnotationImage(image: Coordinator.marker(color: UIColor(hex: marker.colorHex), symbol: marker.symbol),
                                          reuseIdentifier: id)
            }
            guard let report = annotation as? ReportAnnotation else { return nil }
            let id = "report-\(report.typeName)"
            if let reused = mapView.dequeueReusableAnnotationImage(withIdentifier: id) { return reused }
            return MLNAnnotationImage(image: Coordinator.marker(color: UIColor(hex: report.colorHex), symbol: reportSymbol(report.typeName)),
                                      reuseIdentifier: id)
        }

        /// A round coloured marker with a white symbol.
        static func marker(color: UIColor, symbol: String) -> UIImage {
            let size = CGSize(width: 36, height: 36)
            return UIGraphicsImageRenderer(size: size).image { _ in
                let circle = UIBezierPath(ovalIn: CGRect(origin: .zero, size: size).insetBy(dx: 1, dy: 1))
                UIColor.white.setFill()
                circle.fill()
                color.setFill()
                UIBezierPath(ovalIn: CGRect(origin: .zero, size: size).insetBy(dx: 3, dy: 3)).fill()
                let config = UIImage.SymbolConfiguration(pointSize: 16, weight: .bold)
                if let icon = UIImage(systemName: symbol, withConfiguration: config)?.withTintColor(.white, renderingMode: .alwaysOriginal) {
                    icon.draw(in: CGRect(x: (size.width - icon.size.width) / 2, y: (size.height - icon.size.height) / 2,
                                         width: icon.size.width, height: icon.size.height))
                }
            }
        }

        // MARK: Updates

        func apply(_ view: MapView, to map: MLNMapView) {
            self.view = view
            if view.styleUrl != loadedStyleUrl, let url = URL(string: view.styleUrl) {
                loadedStyleUrl = view.styleUrl
                styleLoaded = false
                map.styleURL = url
                return // continues in didFinishLoading
            }
            map.allowsRotating = !view.northUp
            map.compassView.isHidden = !view.showCompass
            if view.northUp && view.drive == nil && map.direction != 0 { map.resetNorth() }
            if view.recenterToken != lastRecenter {
                lastRecenter = view.recenterToken
                following = true
                if view.drive == nil { map.userTrackingMode = .follow }
            }
            if view.overviewToken != lastOverview {
                lastOverview = view.overviewToken
                following = false
                fit(view.overviewShape, on: map, padding: UIEdgeInsets(top: 260, left: 40, bottom: 240, right: 40))
            }
            updateReports(view.reports, on: map)
            updateMarkers(view, on: map)
            guard styleLoaded, let style = map.style else { return }
            updateTraffic(view.traffic, style: style)

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

            removeRouteLayers(style)
            var others: [MLNPolylineFeature] = []
            var main: [MLNPolylineFeature] = []
            for (index, route) in view.routes.enumerated() {
                let line = polyline(route.shape)
                if index == view.selected { main.append(line) } else { others.append(line) }
            }
            addLine(to: style, id: "route-alt", lines: others, color: UIColor(white: 0.62, alpha: 1), width: 6)
            addLine(to: style, id: "route-main", lines: main, color: MapView.indigo, width: 7)

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
                removeRouteLayers(style)
                addLine(to: style, id: "route-main", lines: [polyline(drive.routeShape)], color: MapView.indigo, width: 8)
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
            if following, let view = view {
                let direction = view.northUp ? 0 : (drive.bearingDeg >= 0 ? drive.bearingDeg : map.direction)
                let camera = MLNMapCamera(lookingAtCenter: position, acrossDistance: 700,
                                          pitch: view.tilted ? 45 : 0, heading: direction)
                map.setCamera(camera, animated: true)
            }
        }

        /// Stops (numbered colour), the parked car and the long-pressed point.
        private func updateMarkers(_ view: MapView, on map: MLNMapView) {
            var wanted: [(SearchResult, String, String)] = view.stops.map { ($0, "flag.fill", "#DF8E1D") }
            if let p = view.parking { wanted.append((p, "car.fill", "#1E66F5")) }
            if let p = view.pinned { wanted.append((p, "mappin", "#D20F39")) }
            let key = wanted.map { "\($0.0.lat),\($0.0.lon),\($0.1)" }.joined(separator: "|")
            if key == markersKey { return }
            markersKey = key
            map.removeAnnotations(markers)
            markers = wanted.map { item in
                let m = MarkerAnnotation()
                m.coordinate = CLLocationCoordinate2D(latitude: item.0.lat, longitude: item.0.lon)
                m.symbol = item.1
                m.colorHex = item.2
                return m
            }
            map.addAnnotations(markers)
        }

        private func fit(_ points: [GeoPoint], on map: MLNMapView, padding: UIEdgeInsets) {
            guard let first = points.first, points.count >= 2 else { return }
            var south = first.lat, north = first.lat, west = first.lon, east = first.lon
            for p in points {
                south = min(south, p.lat); north = max(north, p.lat)
                west = min(west, p.lon); east = max(east, p.lon)
            }
            let bounds = MLNCoordinateBounds(sw: CLLocationCoordinate2D(latitude: south, longitude: west),
                                             ne: CLLocationCoordinate2D(latitude: north, longitude: east))
            let camera = map.cameraThatFitsCoordinateBounds(bounds, edgePadding: padding)
            camera.pitch = 0
            map.setCamera(camera, animated: true)
        }

        private func updateReports(_ reports: [ReportPin], on map: MLNMapView) {
            let ids = Set(reports.map { $0.id })
            for (id, pin) in reportPins where !ids.contains(id) {
                map.removeAnnotation(pin)
                reportPins[id] = nil
            }
            for r in reports where reportPins[r.id] == nil {
                let pin = ReportAnnotation()
                pin.reportId = r.id
                pin.typeName = r.typeName
                pin.colorHex = r.colorHex
                pin.coordinate = CLLocationCoordinate2D(latitude: r.lat, longitude: r.lon)
                map.addAnnotation(pin)
                reportPins[r.id] = pin
            }
        }

        private func updateTraffic(_ traffic: [TrafficPolyline], style: MLNStyle) {
            let key = traffic.map { "\($0.level)\($0.points.count)\($0.points.first?.lat ?? 0)" }.joined(separator: "|")
            if key == trafficKey { return }
            trafficKey = key
            for id in ["traffic-jam", "traffic-slow"] {
                if let layer = style.layer(withIdentifier: id) { style.removeLayer(layer) }
                if let source = style.source(withIdentifier: id) { style.removeSource(source) }
            }
            let jams = traffic.filter { $0.level == "JAM" }.map { polyline($0.points) }
            let slow = traffic.filter { $0.level != "JAM" }.map { polyline($0.points) }
            // Under the route lines, so the route stays visible.
            addLine(to: style, id: "traffic-slow", lines: slow, color: UIColor(hex: "#F59F00"), width: 5, belowRoutes: true)
            addLine(to: style, id: "traffic-jam", lines: jams, color: UIColor(hex: "#D20F39"), width: 5, belowRoutes: true)
        }

        private func polyline(_ points: [GeoPoint]) -> MLNPolylineFeature {
            var coordinates = points.map { CLLocationCoordinate2D(latitude: $0.lat, longitude: $0.lon) }
            return MLNPolylineFeature(coordinates: &coordinates, count: UInt(coordinates.count))
        }

        private func removeRouteLayers(_ style: MLNStyle) {
            for id in ["route-main", "route-alt"] {
                if let layer = style.layer(withIdentifier: id) { style.removeLayer(layer) }
                if let source = style.source(withIdentifier: id) { style.removeSource(source) }
            }
        }

        private func addLine(to style: MLNStyle, id: String, lines: [MLNPolylineFeature], color: UIColor, width: Double,
                             belowRoutes: Bool = false) {
            guard !lines.isEmpty else { return }
            let source = MLNShapeSource(identifier: id, features: lines, options: nil)
            style.addSource(source)
            let layer = MLNLineStyleLayer(identifier: id, source: source)
            layer.lineColor = NSExpression(forConstantValue: color)
            layer.lineWidth = NSExpression(forConstantValue: width)
            layer.lineCap = NSExpression(forConstantValue: "round")
            layer.lineJoin = NSExpression(forConstantValue: "round")
            if belowRoutes, let below = style.layer(withIdentifier: "route-alt") ?? style.layer(withIdentifier: "route-main") {
                style.insertLayer(layer, below: below)
            } else {
                style.addLayer(layer)
            }
        }
    }
}
