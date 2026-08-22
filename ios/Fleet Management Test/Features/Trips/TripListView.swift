//
//  TripListView.swift
//  Fleet Management Test
//

import SwiftUI

struct TripListView: View {

    @StateObject private var viewModel: TripListViewModel
    private let repository: any TripRepositoryProtocol

    init(repository: any TripRepositoryProtocol) {
        self.repository = repository
        _viewModel = StateObject(wrappedValue: TripListViewModel(repository: repository))
    }

    var body: some View {
        content
            .navigationTitle("Trips")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { statusMenu }
            }
            .refreshable { await viewModel.refresh() }
            .task { viewModel.loadIfNeeded() }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.phase {
        case .loading:
            ProgressView()
                .controlSize(.large)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        case .empty:
            EmptyState(
                icon: "map",
                title: "No trips",
                message: viewModel.statusFilter == nil
                    ? "No trips have been created yet."
                    : "No trips with that status."
            )
        case .failed(let error):
            EmptyState(
                icon: error.isRetryable ? "wifi.slash" : "exclamationmark.triangle",
                title: error.isRetryable ? "Cannot reach the server" : "Something went wrong",
                message: error.message,
                retry: error.isRetryable ? { viewModel.retry() } : nil
            )
        case .loaded:
            list
        }
    }

    private var list: some View {
        List {
            Section {
                ForEach(viewModel.trips) { trip in
                    NavigationLink {
                        TripDetailView(tripID: trip.id, repository: repository)
                    } label: {
                        TripRow(trip: trip)
                    }
                    .accessibilityIdentifier("trip.row")
                    .onAppear { viewModel.loadMoreIfNeeded(currentItem: trip) }
                }
            } header: {
                Text("\(viewModel.total) trip\(viewModel.total == 1 ? "" : "s")")
            } footer: {
                if viewModel.isLoadingMore {
                    HStack {
                        Spacer()
                        ProgressView().padding(.vertical, 8)
                        Spacer()
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .accessibilityIdentifier("trips.list")
    }

    private var statusMenu: some View {
        Menu {
            Picker("Status", selection: $viewModel.statusFilter) {
                Text("All").tag(TripStatus?.none)
                ForEach(TripStatus.selectableCases, id: \.self) { status in
                    Text(status.displayName).tag(TripStatus?.some(status))
                }
            }
        } label: {
            Label(
                viewModel.statusFilter?.displayName ?? "All",
                systemImage: "line.3.horizontal.decrease.circle"
            )
        }
    }
}

struct TripRow: View {
    let trip: Trip

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(trip.tripNumber)
                    .font(.headline)
                    .monospaced()
                Spacer(minLength: 8)
                StatusBadge(text: trip.status.displayName, tint: tint)
            }
            Text(trip.route)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Label(
                    trip.scheduledStart.formatted(date: .abbreviated, time: .shortened),
                    systemImage: "calendar"
                )
                if let registration = trip.vehicle?.registrationNumber {
                    Label(registration, systemImage: "truck.box")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    private var tint: Color {
        switch trip.status {
        case .completed: return .green
        case .inProgress, .started: return .blue
        case .scheduled: return .orange
        case .cancelled: return .red
        case .unknown: return .secondary
        }
    }
}
