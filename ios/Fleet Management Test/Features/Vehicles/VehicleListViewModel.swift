//
//  VehicleListViewModel.swift
//  Fleet Management Test
//

import Combine
import Foundation

@MainActor
final class VehicleListViewModel: ObservableObject {

    enum Phase: Equatable {
        /// First page, nothing on screen yet. Distinct from `empty` so the user
        /// is never told "no vehicles" while the answer is still in flight.
        case loading
        case loaded
        case empty
        case failed(APIError)
    }

    @Published var searchText = ""
    /// nil is "All".
    @Published var statusFilter: VehicleStatus?

    @Published private(set) var vehicles: [Vehicle] = []
    @Published private(set) var phase: Phase = .loading
    @Published private(set) var isLoadingMore = false
    @Published private(set) var total = 0

    private let repository: any VehicleRepositoryProtocol
    private let pageSize: Int

    private var page = 0
    private var pages = 1
    /// Ids already on screen. The backend paginates over live data, so a vehicle
    /// can shift between pages while the user scrolls; without this it would
    /// appear twice and break List's identity.
    private var seenIDs: Set<String> = []

    /// Bumped whenever the query changes. A response tagged with an older
    /// generation is a filter the user has already moved on from - drop it.
    private var generation = 0
    private var loadTask: Task<Void, Never>?
    private var cancellables: Set<AnyCancellable> = []

    init(repository: any VehicleRepositoryProtocol, pageSize: Int = 20) {
        self.repository = repository
        self.pageSize = pageSize

        // Debounced so typing "tata" is one request, not four. dropFirst()
        // because @Published republishes the current value on subscribe, which
        // would race the initial load.
        $searchText
            .dropFirst()
            .removeDuplicates()
            .debounce(for: .milliseconds(300), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in self?.reload() }
            .store(in: &cancellables)

        // A filter tap is deliberate and discrete; no reason to make it wait.
        $statusFilter
            .dropFirst()
            .removeDuplicates()
            .sink { [weak self] _ in self?.reload() }
            .store(in: &cancellables)
    }

    deinit { loadTask?.cancel() }

    // MARK: - Loading

    /// First load. Safe to call from .task on every appear - it only acts once.
    func loadIfNeeded() {
        guard page == 0, loadTask == nil else { return }
        reload()
    }

    /// Starts over at page 1, keeping what is on screen until the new first
    /// page arrives so the list does not blink on every keystroke.
    func reload() {
        loadTask?.cancel()
        generation += 1
        let generation = self.generation

        if vehicles.isEmpty { phase = .loading }

        loadTask = Task { [weak self] in
            await self?.fetch(page: 1, generation: generation, replacing: true)
        }
    }

    /// Pull to refresh. Awaited so the spinner stays until the request settles.
    func refresh() async {
        loadTask?.cancel()
        generation += 1
        let generation = self.generation
        await fetch(page: 1, generation: generation, replacing: true)
    }

    /// Called as the last row appears.
    func loadMoreIfNeeded(currentItem: Vehicle) {
        guard currentItem.id == vehicles.last?.id else { return }
        guard !isLoadingMore, page < pages, phase == .loaded else { return }

        let generation = self.generation
        let next = page + 1
        isLoadingMore = true
        Task { [weak self] in
            await self?.fetch(page: next, generation: generation, replacing: false)
        }
    }

    func retry() {
        reload()
    }

    // MARK: - The one place that talks to the repository

    private func fetch(page requested: Int, generation: Int, replacing: Bool) async {
        do {
            let result = try await repository.vehicles(
                page: requested,
                pageSize: pageSize,
                search: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                status: statusFilter
            )

            // The user changed the query while this was in flight.
            guard generation == self.generation, !Task.isCancelled else { return }

            if replacing {
                seenIDs = []
                vehicles = []
            }

            let fresh = result.items.filter { seenIDs.insert($0.id).inserted }
            vehicles.append(contentsOf: fresh)

            page = result.page
            pages = result.pages
            total = result.total
            phase = result.isEmpty ? .empty : .loaded
            isLoadingMore = false

        } catch let error as APIError {
            guard generation == self.generation, !Task.isCancelled else { return }
            isLoadingMore = false
            // A failed "load more" must not wipe the pages already on screen.
            if replacing || vehicles.isEmpty {
                phase = .failed(error)
            }
        } catch {
            guard generation == self.generation else { return }
            isLoadingMore = false
        }
    }
}
