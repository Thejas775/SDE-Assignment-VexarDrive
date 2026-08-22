//
//  Coordinate.swift
//  Fleet Management Test
//

import Foundation

/// A latitude/longitude pair parsed out of the API's string decimals.
///
/// Deliberately free of CoreLocation so the model layer stays testable without
/// a location framework; the map view converts to `CLLocationCoordinate2D`.
struct Coordinate: Sendable, Hashable {
    let latitude: Double
    let longitude: Double

    /// Fails when either component is absent or not parseable, which keeps the
    /// "a scheduled trip has no coordinates" case out of the call sites.
    init?(latitude: String?, longitude: String?) {
        guard let latitude, let longitude,
              let lat = Double(latitude), let lon = Double(longitude) else { return nil }
        self.latitude = lat
        self.longitude = lon
    }
}
