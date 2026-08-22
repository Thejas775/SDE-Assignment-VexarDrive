//
//  TripDetailViewModel.swift
//  Fleet Management Test
//

import Combine
import Foundation

@MainActor
final class TripDetailViewModel: ObservableObject {

    enum Phase: Equatable {
        case loading
        case loaded(Trip)
        case failed(APIError)
    }

    enum RoutePhase: Equatable {
        case loading
        /// A trip that never started has no pings. That is normal, not a failure.
        case empty
        case loaded([RoutePoint])
        case failed(APIError)
    }

    @Published private(set) var phase: Phase = .loading
    @Published private(set) var routePhase: RoutePhase = .loading

    let tripID: String
    private let repository: any TripRepositoryProtocol
    private var loadTask: Task<Void, Never>?

    init(tripID: String, repository: any TripRepositoryProtocol) {
        self.tripID = tripID
        self.repository = repository
    }

    deinit { loadTask?.cancel() }

    func loadIfNeeded() {
        guard loadTask == nil else { return }
        load()
    }

    func retry() {
        loadTask?.cancel()
        loadTask = nil
        phase = .loading
        routePhase = .loading
        load()
    }

    private func load() {
        loadTask = Task { [weak self] in
            guard let self else { return }
            // The route does not depend on the trip body, so both go out at
            // once rather than one after the other.
            async let trip = self.fetchTrip()
            async let route = self.fetchRoute()
            let (tripPhase, routePhase) = await (trip, route)

            guard !Task.isCancelled else { return }
            self.phase = tripPhase
            self.routePhase = routePhase
        }
    }

    private func fetchTrip() async -> Phase {
        do {
            return .loaded(try await repository.trip(id: tripID))
        } catch let error as APIError {
            return .failed(error)
        } catch {
            return .failed(APIError(code: APIError.unknownCode,
                                    message: error.localizedDescription))
        }
    }

    private func fetchRoute() async -> RoutePhase {
        do {
            let points = try await repository.route(tripID: tripID, limit: 1000)
            // An empty array is normal - a trip that never started has no pings.
            return points.isEmpty ? .empty : .loaded(points)
        } catch let error as APIError {
            return .failed(error)
        } catch {
            return .failed(APIError(code: APIError.unknownCode,
                                    message: error.localizedDescription))
        }
    }
}
