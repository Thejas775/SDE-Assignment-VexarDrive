//
//  Endpoint.swift
//  Fleet Management Test
//

import Foundation

/// One API call, described rather than performed, so `APIClient` stays the only
/// place that knows about headers, retries and decoding.
struct Endpoint: Sendable {

    enum Method: String, Sendable {
        case get = "GET"
        case post = "POST"
        case patch = "PATCH"
        case delete = "DELETE"
    }

    let method: Method

    /// Relative to `AppConfig.apiBaseURL`, with no leading slash: "auth/login".
    let path: String

    var query: [URLQueryItem] = []
    var body: (any Encodable & Sendable)?

    /// False for the three public auth endpoints. Sending a stale access token
    /// to /auth/login, /auth/register or /auth/refresh is pointless and makes
    /// debugging harder, so those are marked explicitly.
    var requiresAuth: Bool = true
}

// MARK: - Auth

extension Endpoint {

    static func login(email: String, password: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "auth/login",
            body: LoginRequest(email: email, password: password),
            requiresAuth: false
        )
    }

    static func refresh(refreshToken: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "auth/refresh",
            body: RefreshRequest(refreshToken: refreshToken),
            requiresAuth: false
        )
    }

    static func logout(refreshToken: String) -> Endpoint {
        Endpoint(
            method: .post,
            path: "auth/logout",
            body: LogoutRequest(refreshToken: refreshToken)
        )
    }
}

// MARK: - Vehicles

extension Endpoint {

    static func vehicles(
        page: Int,
        pageSize: Int,
        search: String? = nil,
        status: VehicleStatus? = nil
    ) -> Endpoint {
        var query = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "page_size", value: String(pageSize)),
        ]
        // Omit rather than send empty - the backend treats an absent filter and
        // an empty one differently.
        if let search, !search.isEmpty {
            query.append(URLQueryItem(name: "search", value: search))
        }
        if let status, status != .unknown {
            query.append(URLQueryItem(name: "status", value: status.rawValue))
        }
        return Endpoint(method: .get, path: "vehicles", query: query)
    }

    static func vehicle(id: String) -> Endpoint {
        Endpoint(method: .get, path: "vehicles/\(id)")
    }
}

// MARK: - Trips

extension Endpoint {

    static func trips(page: Int, pageSize: Int, status: TripStatus? = nil) -> Endpoint {
        var query = [
            URLQueryItem(name: "page", value: String(page)),
            URLQueryItem(name: "page_size", value: String(pageSize)),
        ]
        if let status, status != .unknown {
            query.append(URLQueryItem(name: "status", value: status.rawValue))
        }
        return Endpoint(method: .get, path: "trips", query: query)
    }

    static func trip(id: String) -> Endpoint {
        Endpoint(method: .get, path: "trips/\(id)")
    }

    static func route(tripID: String, limit: Int = 1000) -> Endpoint {
        Endpoint(
            method: .get,
            path: "trips/\(tripID)/route",
            query: [URLQueryItem(name: "limit", value: String(limit))]
        )
    }
}
