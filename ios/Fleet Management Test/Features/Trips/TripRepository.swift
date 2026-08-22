//
//  TripRepository.swift
//  Fleet Management Test
//

import Foundation

protocol TripRepositoryProtocol: Sendable {
    func trips(page: Int, pageSize: Int, status: TripStatus?) async throws -> Page<Trip>
    func trip(id: String) async throws -> Trip
    func route(tripID: String, limit: Int) async throws -> [RoutePoint]
}

struct TripRepository: TripRepositoryProtocol {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func trips(page: Int, pageSize: Int, status: TripStatus?) async throws -> Page<Trip> {
        try await client.send(
            .trips(page: page, pageSize: pageSize, status: status),
            as: Page<Trip>.self
        )
    }

    func trip(id: String) async throws -> Trip {
        try await client.send(.trip(id: id), as: Trip.self)
    }

    /// A plain array, not a Page - the one list endpoint that is not paginated.
    func route(tripID: String, limit: Int = 1000) async throws -> [RoutePoint] {
        try await client.send(.route(tripID: tripID, limit: limit), as: [RoutePoint].self)
    }
}
