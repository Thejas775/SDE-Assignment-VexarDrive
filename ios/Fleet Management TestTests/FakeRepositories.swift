//
//  FakeRepositories.swift
//  Fleet Management TestTests
//

import Foundation
@testable import Fleet_Management_Test

/// Serves pages from a fixed corpus and records every call.
final class FakeVehicleRepository: VehicleRepositoryProtocol, @unchecked Sendable {

    struct Call: Equatable {
        let page: Int
        let search: String?
        let status: VehicleStatus?
    }

    private let lock = NSLock()
    private var _calls: [Call] = []
    var calls: [Call] { lock.withLock { _calls } }

    var corpus: [Vehicle]
    var failNextWith: APIError?
    /// Lets a test hold a request open and act while it is in flight.
    var delay: UInt64 = 0

    init(count: Int) {
        corpus = count == 0 ? [] : (1...count).map(Fixtures.vehicle(index:))
    }

    func vehicles(
        page: Int, pageSize: Int, search: String?, status: VehicleStatus?
    ) async throws -> Page<Vehicle> {
        lock.withLock { _calls.append(Call(page: page, search: search, status: status)) }
        if delay > 0 { try? await Task.sleep(nanoseconds: delay) }
        if let error = failNextWith {
            failNextWith = nil
            throw error
        }

        let matching = (search?.isEmpty == false)
            ? corpus.filter { $0.registrationNumber.lowercased().contains(search!.lowercased()) }
            : corpus
        let start = (page - 1) * pageSize
        let items = start < matching.count
            ? Array(matching[start..<min(start + pageSize, matching.count)])
            : []
        let pages = matching.isEmpty
            ? 0
            : Int(ceil(Double(matching.count) / Double(pageSize)))
        return Page(items: items, total: matching.count, page: page,
                    pageSize: pageSize, pages: pages)
    }

    func vehicle(id: String) async throws -> Vehicle {
        guard let match = corpus.first(where: { $0.id == id }) else {
            throw APIError(code: 404, message: "Not found.")
        }
        return match
    }
}

final class FakeTripRepository: TripRepositoryProtocol, @unchecked Sendable {

    var tripResult: Result<Trip, APIError>
    var routeResult: Result<[RoutePoint], APIError>

    private let lock = NSLock()
    private var _tripCalls = 0
    var tripCalls: Int { lock.withLock { _tripCalls } }

    init(trip: Result<Trip, APIError>, route: Result<[RoutePoint], APIError>) {
        tripResult = trip
        routeResult = route
    }

    func trips(page: Int, pageSize: Int, status: TripStatus?) async throws -> Page<Trip> {
        Page(items: [], total: 0, page: 1, pageSize: pageSize, pages: 0)
    }

    func trip(id: String) async throws -> Trip {
        lock.withLock { _tripCalls += 1 }
        return try tripResult.get()
    }

    func route(tripID: String, limit: Int) async throws -> [RoutePoint] {
        try routeResult.get()
    }
}

final class FakeAuthRepository: AuthRepositoryProtocol, @unchecked Sendable {

    var loginResult: Result<User, APIError> = .success(Fixtures.user)
    var stored: User?

    private let lock = NSLock()
    private var _loginCalls: [(email: String, password: String)] = []
    var loginCalls: [(email: String, password: String)] { lock.withLock { _loginCalls } }
    private(set) var didLogout = false

    func login(email: String, password: String) async throws -> User {
        lock.withLock { _loginCalls.append((email, password)) }
        // Mirrors the real repository, which normalises before sending.
        let user = try loginResult.get()
        stored = user
        return user
    }

    func logout() async {
        didLogout = true
        stored = nil
    }

    func restore() -> User? { stored }
}
