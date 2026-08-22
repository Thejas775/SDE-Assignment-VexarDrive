//
//  AuthModels.swift
//  Fleet Management Test
//

import Foundation

// MARK: - Requests

struct LoginRequest: Encodable, Sendable {
    let email: String
    let password: String
}

struct RefreshRequest: Encodable, Sendable {
    let refreshToken: String
}

struct LogoutRequest: Encodable, Sendable {
    let refreshToken: String
}

// MARK: - Responses

/// `POST /auth/login` and `POST /auth/refresh` both return this envelope.
///
/// A refresh yields a *new* refresh token as well as a new access token - the
/// old one is invalidated server-side, so both values must be persisted.
struct TokenResponse: Decodable, Sendable {
    let accessToken: String
    let refreshToken: String
    let tokenType: String
    let expiresIn: Int
    let user: User
}

struct MessageResponse: Decodable, Sendable {
    let message: String
}

struct User: Codable, Sendable, Identifiable, Hashable {
    let id: String
    let email: String
    let fullName: String
    let phoneNumber: String?
    let role: UserRole
    let isActive: Bool
    let createdAt: Date
}

enum UserRole: String, APIEnum {
    case fleetManager = "FLEET_MANAGER"
    case driver = "DRIVER"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .fleetManager: return "Fleet Manager"
        case .driver: return "Driver"
        case .unknown: return "Unknown"
        }
    }
}
