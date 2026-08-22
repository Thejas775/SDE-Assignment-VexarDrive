//
//  VehicleListViewModelTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

@MainActor
final class VehicleListViewModelTests: XCTestCase {

    private func settle(_ milliseconds: UInt64 = 150) async {
        try? await Task.sleep(nanoseconds: milliseconds * 1_000_000)
    }

    func testFirstPageLoads() async {
        let repository = FakeVehicleRepository(count: 45)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)

        viewModel.loadIfNeeded()
        await settle()

        XCTAssertEqual(viewModel.vehicles.count, 20)
        XCTAssertEqual(viewModel.total, 45)
        XCTAssertEqual(viewModel.phase, .loaded)
        XCTAssertEqual(repository.calls.count, 1)
    }

    func testScrollingAppendsWithoutDuplicatingOrSkipping() async {
        let repository = FakeVehicleRepository(count: 45)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle()

        viewModel.loadMoreIfNeeded(currentItem: viewModel.vehicles.last!)
        await settle()
        XCTAssertEqual(viewModel.vehicles.count, 40)
        XCTAssertEqual(Set(viewModel.vehicles.map(\.id)).count, 40, "no duplicates")
        XCTAssertEqual(viewModel.vehicles.first?.id, "v1")
        XCTAssertEqual(viewModel.vehicles.last?.id, "v40", "no skipped rows")

        viewModel.loadMoreIfNeeded(currentItem: viewModel.vehicles.last!)
        await settle()
        XCTAssertEqual(viewModel.vehicles.count, 45)
    }

    func testStopsAtTheLastPage() async {
        let repository = FakeVehicleRepository(count: 25)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle()

        viewModel.loadMoreIfNeeded(currentItem: viewModel.vehicles.last!)
        await settle()
        XCTAssertEqual(viewModel.vehicles.count, 25)

        let callsAtEnd = repository.calls.count
        viewModel.loadMoreIfNeeded(currentItem: viewModel.vehicles.last!)
        await settle()
        XCTAssertEqual(repository.calls.count, callsAtEnd, "no request past the last page")
    }

    /// Rows appear in a burst as the user flicks; each one asks to load more.
    func testBurstOfAppearancesRequestsPageTwoOnce() async {
        let repository = FakeVehicleRepository(count: 100)
        repository.delay = 60_000_000
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle(200)

        let last = viewModel.vehicles.last!
        viewModel.loadMoreIfNeeded(currentItem: last)
        viewModel.loadMoreIfNeeded(currentItem: last)
        viewModel.loadMoreIfNeeded(currentItem: last)
        await settle(250)

        XCTAssertEqual(repository.calls.filter { $0.page == 2 }.count, 1)
        XCTAssertEqual(Set(viewModel.vehicles.map(\.id)).count, viewModel.vehicles.count)
    }

    func testSearchIsDebouncedIntoASingleRequest() async {
        let repository = FakeVehicleRepository(count: 45)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle()
        let baseline = repository.calls.count

        for text in ["t", "ta", "tat", "tata"] { viewModel.searchText = text }
        await settle(600)

        XCTAssertEqual(repository.calls.count - baseline, 1, "four keystrokes, one request")
        XCTAssertEqual(repository.calls.last?.search, "tata")
        XCTAssertEqual(repository.calls.last?.page, 1, "a new search restarts at page 1")
    }

    func testSearchAndStatusFilterCombine() async {
        let repository = FakeVehicleRepository(count: 45)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle()

        viewModel.searchText = "KA-01"
        await settle(600)
        viewModel.statusFilter = .available
        await settle(250)

        XCTAssertEqual(repository.calls.last?.search, "KA-01")
        XCTAssertEqual(repository.calls.last?.status, .available)

        viewModel.statusFilter = nil
        await settle(250)
        XCTAssertNil(repository.calls.last?.status, "clearing the filter drops the parameter")
    }

    func testEmptyIsDistinctFromLoading() async {
        let repository = FakeVehicleRepository(count: 0)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)

        XCTAssertEqual(viewModel.phase, .loading)
        viewModel.loadIfNeeded()
        await settle()

        XCTAssertEqual(viewModel.phase, .empty)
        XCTAssertTrue(viewModel.vehicles.isEmpty)
    }

    func testFirstPageFailureSurfacesAndRetryRecovers() async {
        let repository = FakeVehicleRepository(count: 45)
        repository.failNextWith = APIError.offline()
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)

        viewModel.loadIfNeeded()
        await settle()
        XCTAssertEqual(viewModel.phase, .failed(APIError.offline()))

        viewModel.retry()
        await settle()
        XCTAssertEqual(viewModel.phase, .loaded)
        XCTAssertEqual(viewModel.vehicles.count, 20)
    }

    /// A failed page 2 must leave page 1 on screen.
    func testFailedLoadMoreKeepsTheLoadedPages() async {
        let repository = FakeVehicleRepository(count: 45)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle()

        repository.failNextWith = APIError.offline()
        viewModel.loadMoreIfNeeded(currentItem: viewModel.vehicles.last!)
        await settle()

        XCTAssertEqual(viewModel.vehicles.count, 20)
        XCTAssertEqual(viewModel.phase, .loaded)
    }

    /// A response for a query the user has already moved past must be dropped.
    func testStaleResponseIsDiscarded() async {
        let repository = FakeVehicleRepository(count: 45)
        repository.delay = 200_000_000
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)

        viewModel.loadIfNeeded()
        viewModel.searchText = "KA-01-AB-1001"
        await settle(1000)

        XCTAssertEqual(repository.calls.last?.search, "KA-01-AB-1001")
        XCTAssertEqual(viewModel.vehicles.count, 1)
    }

    func testPullToRefreshRestartsAtPageOne() async {
        let repository = FakeVehicleRepository(count: 45)
        let viewModel = VehicleListViewModel(repository: repository, pageSize: 20)
        viewModel.loadIfNeeded()
        await settle()
        viewModel.loadMoreIfNeeded(currentItem: viewModel.vehicles.last!)
        await settle()
        XCTAssertEqual(viewModel.vehicles.count, 40)

        await viewModel.refresh()

        XCTAssertEqual(repository.calls.last?.page, 1)
        XCTAssertEqual(viewModel.vehicles.count, 20)
        XCTAssertEqual(Set(viewModel.vehicles.map(\.id)).count, 20)
    }
}
