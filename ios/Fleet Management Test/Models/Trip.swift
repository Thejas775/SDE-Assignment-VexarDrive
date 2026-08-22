//
//  Trip.swift
//  Fleet Management Test
//

import Foundation

/// A trip. Almost everything describing what *actually* happened is null until
/// the trip runs, so a `SCHEDULED` trip arrives with ~9 null fields.
struct Trip: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let tripNumber: String
    let vehicle: TripVehicle?
    let driver: TripDriver?
    let source: String
    let destination: String
    let scheduledStart: Date
    let scheduledEnd: Date
    let status: TripStatus
    let actualStart: Date?
    let actualEnd: Date?
    let startOdometer: Int?
    let endOdometer: Int?

    // Decimals arrive as strings so they never round through a float.
    // Kept as strings for display; parsed only where arithmetic is needed.
    let startLatitude: String?
    let startLongitude: String?
    let endLatitude: String?
    let endLongitude: String?
    let distanceKm: String?

    let notes: String?
    let createdAt: Date
    let durationMinutes: Int?

    var route: String { "\(source) → \(destination)" }

    var startCoordinate: Coordinate? {
        Coordinate(latitude: startLatitude, longitude: startLongitude)
    }

    var endCoordinate: Coordinate? {
        Coordinate(latitude: endLatitude, longitude: endLongitude)
    }
}

/// The trimmed vehicle the trips endpoints embed - not a full `Vehicle`.
struct TripVehicle: Decodable, Sendable, Hashable {
    let id: String
    let registrationNumber: String
    let make: String
    let model: String

    var makeModel: String { "\(make) \(model)" }
}

struct TripDriver: Decodable, Sendable, Hashable {
    let id: String
    let fullName: String
    let licenseNumber: String
}

enum TripStatus: String, APIEnum {
    case scheduled = "SCHEDULED"
    case started = "STARTED"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case cancelled = "CANCELLED"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .scheduled: return "Scheduled"
        case .started: return "Started"
        case .inProgress: return "In progress"
        case .completed: return "Completed"
        case .cancelled: return "Cancelled"
        case .unknown: return "Unknown"
        }
    }
}

/// One GPS ping. `GET /trips/{id}/route` returns these as a plain array,
/// oldest first - no pagination envelope.
struct RoutePoint: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let tripId: String
    let vehicleId: String
    let latitude: String
    let longitude: String
    let speedKmph: String?
    let heading: String?
    let accuracyM: String?
    let recordedAt: Date
    let receivedAt: Date

    var coordinate: Coordinate? { Coordinate(latitude: latitude, longitude: longitude) }
}
