//
//  VehicleListView.swift
//  Fleet Management Test
//

import SwiftUI

struct VehicleListView: View {

    @StateObject private var viewModel: VehicleListViewModel
    @EnvironmentObject private var session: SessionController

    init(repository: any VehicleRepositoryProtocol) {
        _viewModel = StateObject(wrappedValue: VehicleListViewModel(repository: repository))
    }

    var body: some View {
        content
            .navigationTitle("Vehicles")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { statusMenu }
                ToolbarItem(placement: .topBarTrailing) { signOutButton }
            }
            .searchable(
                text: $viewModel.searchText,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Registration, make or model"
            )
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
            emptyState
        case .failed(let error):
            failureState(error)
        case .loaded:
            list
        }
    }

    private var emptyState: some View {
        let filtered = !viewModel.searchText.isEmpty || viewModel.statusFilter != nil
        return EmptyState(
            icon: "magnifyingglass",
            title: "No vehicles found",
            message: filtered
                ? "Nothing matches that search and filter."
                : "There are no vehicles in the fleet yet."
        )
    }

    private func failureState(_ error: APIError) -> some View {
        EmptyState(
            icon: error.isRetryable ? "wifi.slash" : "exclamationmark.triangle",
            title: error.isRetryable ? "Cannot reach the server" : "Something went wrong",
            message: error.message,
            retry: error.isRetryable ? { viewModel.retry() } : nil
        )
    }

    private var list: some View {
        List {
            Section {
                ForEach(viewModel.vehicles) { vehicle in
                    VehicleRow(vehicle: vehicle)
                        .onAppear { viewModel.loadMoreIfNeeded(currentItem: vehicle) }
                }
            } header: {
                Text("\(viewModel.total) vehicle\(viewModel.total == 1 ? "" : "s")")
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
    }

    private var statusMenu: some View {
        Menu {
            Picker("Status", selection: $viewModel.statusFilter) {
                Text("All").tag(VehicleStatus?.none)
                ForEach(VehicleStatus.selectableCases, id: \.self) { status in
                    Text(status.displayName).tag(VehicleStatus?.some(status))
                }
            }
        } label: {
            Label(
                viewModel.statusFilter?.displayName ?? "All",
                systemImage: "line.3.horizontal.decrease.circle"
            )
        }
    }

    private var signOutButton: some View {
        Button("Sign out") {
            Task { await session.signOut() }
        }
    }
}

// MARK: - Row

struct VehicleRow: View {
    let vehicle: Vehicle

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(vehicle.registrationNumber)
                    .font(.headline)
                    .monospaced()
                Spacer(minLength: 8)
                StatusBadge(text: vehicle.status.displayName, tint: tint)
            }

            Text(vehicle.makeModelYear)
                .font(.subheadline)
                .foregroundStyle(.secondary)

            HStack(spacing: 12) {
                Label("\(vehicle.currentMileage.formatted()) km", systemImage: "gauge.medium")
                if vehicle.hasExpiringDocument {
                    Label(expiringLabel, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private var expiringLabel: String {
        switch (vehicle.insuranceExpiringSoon, vehicle.registrationExpiringSoon) {
        case (true, true): return "Insurance & registration expiring"
        case (true, false): return "Insurance expiring"
        default: return "Registration expiring"
        }
    }

    private var tint: Color {
        switch vehicle.status {
        case .available: return .green
        case .onTrip: return .blue
        case .inMaintenance: return .orange
        case .inactive, .unknown: return .secondary
        }
    }
}

// MARK: - Shared small views

struct StatusBadge: View {
    let text: String
    let tint: Color

    var body: some View {
        Text(text.uppercased())
            .font(.caption2.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(tint.opacity(0.15), in: Capsule())
    }
}

struct EmptyState: View {
    let icon: String
    let title: String
    var message: String?
    var retry: (() -> Void)?

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 40))
                .foregroundStyle(.secondary)
                .padding(.bottom, 4)
            Text(title)
                .font(.headline)
            if let message {
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            if let retry {
                Button("Try again", action: retry)
                    .buttonStyle(.bordered)
                    .padding(.top, 4)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
    }
}
