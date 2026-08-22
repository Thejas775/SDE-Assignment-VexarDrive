//
//  TripListViewModel.swift
//  Fleet Management Test
//

import Combine
import Foundation

/// Deliberately thinner than the vehicle list: its job is to reach Trip Details,
/// which is the screen the brief actually asks for. Paging only, no search.
@MainActor
final class TripListViewModel: ObservableObject {

    enum Phase: Equatable {
        case loading
        case loaded
        case empty
        case failed(APIError)
    }

    @Published var statusFilter: TripStatus?
    @Published private(set) var trips: [Trip] = []
    @Published private(set) var phase: Phase = .loading
    @Published private(set) var isLoadingMore = false
    @Published private(set) var total = 0

    private let repository: any TripRepositoryProtocol
    private let pageSize: Int

    private var page = 0
    private var pages = 1
    private var seenIDs: Set<String> = []
    private var generation = 0
    private var loadTask: Task<Void, Never>?
    private var cancellables: Set<AnyCancellable> = []

    init(repository: any TripRepositoryProtocol, pageSize: Int = 20) {
        self.repository = repository
        self.pageSize = pageSize

        $statusFilter
            .dropFirst()
            .removeDuplicates()
            .sink { [weak self] _ in self?.reload() }
            .store(in: &cancellables)
    }

    deinit { loadTask?.cancel() }

    func loadIfNeeded() {
        guard page == 0, loadTask == nil else { return }
        reload()
    }

    func reload() {
        loadTask?.cancel()
        generation += 1
        let generation = self.generation
        if trips.isEmpty { phase = .loading }
        loadTask = Task { [weak self] in
            await self?.fetch(page: 1, generation: generation, replacing: true)
        }
    }

    func refresh() async {
        loadTask?.cancel()
        generation += 1
        await fetch(page: 1, generation: generation, replacing: true)
    }

    func loadMoreIfNeeded(currentItem: Trip) {
        guard currentItem.id == trips.last?.id else { return }
        guard !isLoadingMore, page < pages, phase == .loaded else { return }
        let generation = self.generation
        let next = page + 1
        isLoadingMore = true
        Task { [weak self] in
            await self?.fetch(page: next, generation: generation, replacing: false)
        }
    }

    func retry() { reload() }

    private func fetch(page requested: Int, generation: Int, replacing: Bool) async {
        do {
            let result = try await repository.trips(
                page: requested, pageSize: pageSize, status: statusFilter
            )
            guard generation == self.generation, !Task.isCancelled else { return }

            if replacing {
                seenIDs = []
                trips = []
            }
            trips.append(contentsOf: result.items.filter { seenIDs.insert($0.id).inserted })

            page = result.page
            pages = result.pages
            total = result.total
            phase = result.isEmpty ? .empty : .loaded
            isLoadingMore = false
        } catch let error as APIError {
            guard generation == self.generation, !Task.isCancelled else { return }
            isLoadingMore = false
            if replacing || trips.isEmpty { phase = .failed(error) }
        } catch {
            guard generation == self.generation else { return }
            isLoadingMore = false
        }
    }
}
