//
//  RouteMapView.swift
//  Fleet Management Test
//

import MapKit
import SwiftUI

/// The route drawn as a polyline.
///
/// MKMapView through UIViewRepresentable rather than SwiftUI's Map: MapPolyline
/// is iOS 17 and the deployment target is 16, and this avoids splitting the
/// view into two availability branches.
struct RouteMapView: UIViewRepresentable {

    let points: [RoutePoint]

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.isRotateEnabled = false
        map.isPitchEnabled = false
        map.showsCompass = false
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        map.removeOverlays(map.overlays)
        map.removeAnnotations(map.annotations)

        let coordinates = points.compactMap(\.coordinate).map {
            CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude)
        }
        guard !coordinates.isEmpty else { return }

        let polyline = MKPolyline(coordinates: coordinates, count: coordinates.count)
        map.addOverlay(polyline)

        // Points arrive oldest first, so the ends are the start and finish.
        if let first = coordinates.first {
            map.addAnnotation(EndPoint(coordinate: first, title: "Start"))
        }
        if coordinates.count > 1, let last = coordinates.last {
            map.addAnnotation(EndPoint(coordinate: last, title: "End"))
        }

        map.setVisibleMapRect(
            polyline.boundingMapRect,
            edgePadding: UIEdgeInsets(top: 32, left: 32, bottom: 32, right: 32),
            animated: false
        )
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MKMapViewDelegate {
        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            guard let polyline = overlay as? MKPolyline else {
                return MKOverlayRenderer(overlay: overlay)
            }
            let renderer = MKPolylineRenderer(polyline: polyline)
            renderer.strokeColor = UIColor.tintColor
            renderer.lineWidth = 4
            renderer.lineCap = .round
            return renderer
        }
    }

    final class EndPoint: NSObject, MKAnnotation {
        let coordinate: CLLocationCoordinate2D
        let title: String?

        init(coordinate: CLLocationCoordinate2D, title: String) {
            self.coordinate = coordinate
            self.title = title
        }
    }
}
