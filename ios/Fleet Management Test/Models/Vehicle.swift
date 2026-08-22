//
//  Vehicle.swift
//  Fleet Management Test
//

import Foundation

struct Vehicle: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let registrationNumber: String
    let vehicleType: VehicleType
    let make: String
    let model: String
    let year: Int
    let fuelType: FuelType
    let currentMileage: Int
    let status: VehicleStatus
    let insuranceExpiry: Date?
    let registrationExpiry: Date?
    let createdAt: Date
    let updatedAt: Date
    let insuranceExpiringSoon: Bool
    let registrationExpiringSoon: Bool

    /// "Tata Ace · 2022" - the secondary line of a list row.
    var makeModelYear: String { "\(make) \(model) · \(year)" }

    var hasExpiringDocument: Bool { insuranceExpiringSoon || registrationExpiringSoon }
}

enum VehicleStatus: String, APIEnum {
    case available = "AVAILABLE"
    case onTrip = "ON_TRIP"
    case inMaintenance = "IN_MAINTENANCE"
    case inactive = "INACTIVE"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .available: return "Available"
        case .onTrip: return "On trip"
        case .inMaintenance: return "In maintenance"
        case .inactive: return "Inactive"
        case .unknown: return "Unknown"
        }
    }
}

enum VehicleType: String, APIEnum {
    case truck = "TRUCK"
    case van = "VAN"
    case car = "CAR"
    case pickup = "PICKUP"
    case bus = "BUS"
    case twoWheeler = "TWO_WHEELER"
    case trailer = "TRAILER"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .truck: return "Truck"
        case .van: return "Van"
        case .car: return "Car"
        case .pickup: return "Pickup"
        case .bus: return "Bus"
        case .twoWheeler: return "Two-wheeler"
        case .trailer: return "Trailer"
        case .unknown: return "Unknown"
        }
    }
}

enum FuelType: String, APIEnum {
    case petrol = "PETROL"
    case diesel = "DIESEL"
    case cng = "CNG"
    case lpg = "LPG"
    case electric = "ELECTRIC"
    case hybrid = "HYBRID"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .petrol: return "Petrol"
        case .diesel: return "Diesel"
        case .cng: return "CNG"
        case .lpg: return "LPG"
        case .electric: return "Electric"
        case .hybrid: return "Hybrid"
        case .unknown: return "Unknown"
        }
    }
}
