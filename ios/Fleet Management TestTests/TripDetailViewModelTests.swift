//
//  TripDetailViewModelTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

@MainActor
final class TripDetailViewModelTests: XCTestCase {

    private func settle(_ milliseconds: UInt64 = 150) async {
        try? await Task.sleep(nanoseconds: milliseconds * 1_000_000)
    }

    private func trip(_ data: Data) throws -> Trip {
        try JSONCoding.decoder.decode(Trip.self, from: data)
    }

    private func routePoints() throws -> [RoutePoint] {
        try JSONCoding.decoder.decode([RoutePoint].self, from: Fixtures.routeArray)
    }

    func testCompletedTripShowsDistanceAndBothOdometerReadings() async throws {
        let repository = FakeTripRepository(
            trip: .success(try trip(Fixtures.completedTrip)),
            route: .success(try routePoints())
        )
        let viewModel = TripDetailViewModel(tripID: "t1", repository: repository)

        viewModel.loadIfNeeded()
        await settle()

        guard case .loaded(let loaded) = viewModel.phase else {
            return XCTFail("expected a loaded trip, got \(viewModel.phase)")
        }
        XCTAssertEqual(loaded.distanceKm, "346.00")
        XCTAssertEqual(loaded.startOdometer, 48250)
        XCTAssertEqual(loaded.endOdometer, 48596)
    }

    /// The awkward case: nine null fields and it must still render.
    func testScheduledTripRendersDespiteNullFields() async throws {
        let repository = FakeTripRepository(
            trip: .success(try trip(Fixtures.scheduledTrip)),
            route: .success([])
        )
        let viewModel = TripDetailViewModel(tripID: "t2", repository: repository)

        viewModel.loadIfNeeded()
        await settle()

        guard case .loaded(let loaded) = viewModel.phase else {
            return XCTFail("expected a loaded trip, got \(viewModel.phase)")
        }
        XCTAssertEqual(loaded.status, .scheduled)
        XCTAssertNil(loaded.actualStart)
        XCTAssertNil(loaded.distanceKm)
        XCTAssertNil(loaded.startCoordinate)
        XCTAssertNil(loaded.vehicle)
    }

    /// A trip that never started has no pings. That is an empty state, not a
    /// spinner that never resolves.
    func testEmptyRouteIsAnEmptyState() async throws {
        let repository = FakeTripRepository(
            trip: .success(try trip(Fixtures.scheduledTrip)),
            route: .success([])
        )
        let viewModel = TripDetailViewModel(tripID: "t2", repository: repository)

        viewModel.loadIfNeeded()
        await settle()

        XCTAssertEqual(viewModel.routePhase, .empty)
    }

    func testForbiddenTripShowsAClearMessageAndNoRetry() async {
        let denied = APIError(code: 403, message: "Not your trip")
        let repository = FakeTripRepository(trip: .failure(denied), route: .failure(denied))
        let viewModel = TripDetailViewModel(tripID: "t3", repository: repository)

        viewModel.loadIfNeeded()
        await settle()

        guard case .failed(let error) = viewModel.phase else {
            return XCTFail("expected a failure, got \(viewModel.phase)")
        }
        XCTAssertTrue(error.isForbidden)
        XCTAssertEqual(error.message, "Not your trip")
        XCTAssertFalse(error.isRetryable)
    }

    /// A route that fails must not take the whole screen down with it.
    func testRouteFailureIsConfinedToItsSection() async throws {
        let repository = FakeTripRepository(
            trip: .success(try trip(Fixtures.completedTrip)),
            route: .failure(APIError.offline())
        )
        let viewModel = TripDetailViewModel(tripID: "t1", repository: repository)

        viewModel.loadIfNeeded()
        await settle()

        guard case .loaded = viewModel.phase else {
            return XCTFail("the trip itself should still render")
        }
        XCTAssertEqual(viewModel.routePhase, .failed(APIError.offline()))
    }

    func testRepeatedAppearancesLoadOnce() async throws {
        let repository = FakeTripRepository(
            trip: .success(try trip(Fixtures.completedTrip)), route: .success([])
        )
        let viewModel = TripDetailViewModel(tripID: "t1", repository: repository)

        viewModel.loadIfNeeded()
        viewModel.loadIfNeeded()
        viewModel.loadIfNeeded()
        await settle()

        XCTAssertEqual(repository.tripCalls, 1)
    }

    func testRetryRecoversAfterAnOfflineFailure() async throws {
        let repository = FakeTripRepository(
            trip: .failure(APIError.offline()), route: .failure(APIError.offline())
        )
        let viewModel = TripDetailViewModel(tripID: "t1", repository: repository)

        viewModel.loadIfNeeded()
        await settle()
        XCTAssertEqual(viewModel.phase, .failed(APIError.offline()))

        repository.tripResult = .success(try trip(Fixtures.completedTrip))
        repository.routeResult = .success(try routePoints())
        viewModel.retry()
        await settle()

        guard case .loaded = viewModel.phase else {
            return XCTFail("expected recovery, got \(viewModel.phase)")
        }
    }
}
