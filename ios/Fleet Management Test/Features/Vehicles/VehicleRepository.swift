//
//  VehicleRepository.swift
//  Fleet Management Test
//

import Foundation

protocol VehicleRepositoryProtocol: Sendable {
    func vehicles(
        page: Int,
        pageSize: Int,
        search: String?,
        status: VehicleStatus?
    ) async throws -> Page<Vehicle>

    func vehicle(id: String) async throws -> Vehicle
}

struct VehicleRepository: VehicleRepositoryProtocol {

    private let client: APIClient

    init(client: APIClient) {
        self.client = client
    }

    func vehicles(
        page: Int,
        pageSize: Int,
        search: String?,
        status: VehicleStatus?
    ) async throws -> Page<Vehicle> {
        try await client.send(
            .vehicles(page: page, pageSize: pageSize, search: search, status: status),
            as: Page<Vehicle>.self
        )
    }

    func vehicle(id: String) async throws -> Vehicle {
        try await client.send(.vehicle(id: id), as: Vehicle.self)
    }
}
