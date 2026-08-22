//
//  TripDetailView.swift
//  Fleet Management Test
//

import SwiftUI

struct TripDetailView: View {

    @StateObject private var viewModel: TripDetailViewModel

    init(tripID: String, repository: any TripRepositoryProtocol) {
        _viewModel = StateObject(
            wrappedValue: TripDetailViewModel(tripID: tripID, repository: repository)
        )
    }

    var body: some View {
        content
            .navigationTitle("Trip")
            .navigationBarTitleDisplayMode(.inline)
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
        case .failed(let error):
            failure(error)
        case .loaded(let trip):
            loaded(trip)
        }
    }

    private func failure(_ error: APIError) -> some View {
        EmptyState(
            icon: icon(for: error),
            title: title(for: error),
            message: error.message,
            retry: error.isRetryable ? { viewModel.retry() } : nil
        )
    }

    private func icon(for error: APIError) -> String {
        if error.isForbidden { return "lock.fill" }
        if error.isNotFound { return "questionmark.folder" }
        return error.isRetryable ? "wifi.slash" : "exclamationmark.triangle"
    }

    private func title(for error: APIError) -> String {
        if error.isForbidden { return "Not your trip" }
        if error.isNotFound { return "Trip not found" }
        return error.isRetryable ? "Cannot reach the server" : "Something went wrong"
    }

    private func loaded(_ trip: Trip) -> some View {
        List {
            headerSection(trip)
            vehicleAndDriverSection(trip)
            scheduleSection(trip)
            journeySection(trip)
            if let notes = trip.notes, !notes.isEmpty {
                Section("Notes") { Text(notes) }
            }
            routeSection
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Sections

    private func headerSection(_ trip: Trip) -> some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(trip.tripNumber)
                        .font(.title2.bold())
                        .monospaced()
                    Spacer()
                    StatusBadge(text: trip.status.displayName, tint: tint(for: trip.status))
                }
                Text(trip.route)
                    .font(.headline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.vertical, 4)
        }
    }

    @ViewBuilder
    private func vehicleAndDriverSection(_ trip: Trip) -> some View {
        if let vehicle = trip.vehicle {
            Section("Vehicle") {
                DetailRow(label: "Registration", value: vehicle.registrationNumber, monospaced: true)
                DetailRow(label: "Make and model", value: vehicle.makeModel)
            }
        }
        if let driver = trip.driver {
            Section("Driver") {
                DetailRow(label: "Name", value: driver.fullName)
                DetailRow(label: "Licence", value: driver.licenseNumber, monospaced: true)
            }
        }
    }

    private func scheduleSection(_ trip: Trip) -> some View {
        Section("Scheduled") {
            DetailRow(label: "Start", value: Self.format(trip.scheduledStart))
            DetailRow(label: "End", value: Self.format(trip.scheduledEnd))
        }
    }

    /// Everything a trip only has once it has actually run. A SCHEDULED trip
    /// has none of it, so the whole section stays off screen rather than
    /// showing a column of dashes.
    @ViewBuilder
    private func journeySection(_ trip: Trip) -> some View {
        let hasActuals = trip.actualStart != nil || trip.actualEnd != nil
        let hasReadings = trip.startOdometer != nil || trip.endOdometer != nil
            || trip.distanceKm != nil || trip.durationMinutes != nil

        if hasActuals || hasReadings {
            Section("Journey") {
                if let start = trip.actualStart {
                    DetailRow(label: "Actual start", value: Self.format(start))
                }
                if let end = trip.actualEnd {
                    DetailRow(label: "Actual end", value: Self.format(end))
                }
                if let start = trip.startOdometer {
                    DetailRow(label: "Odometer at start", value: "\(start.formatted()) km")
                }
                if let end = trip.endOdometer {
                    DetailRow(label: "Odometer at end", value: "\(end.formatted()) km")
                }
                // Shown exactly as the server sent it - it is a decimal string
                // on purpose and reads correctly as-is.
                if let distance = trip.distanceKm {
                    DetailRow(label: "Distance", value: "\(distance) km")
                }
                if let minutes = trip.durationMinutes {
                    DetailRow(label: "Duration", value: Self.formatDuration(minutes))
                }
            }
        }
    }

    @ViewBuilder
    private var routeSection: some View {
        Section("Route") {
            switch viewModel.routePhase {
            case .loading:
                HStack {
                    ProgressView()
                    Text("Loading route…").foregroundStyle(.secondary)
                }
            case .empty:
                Label("No GPS points recorded for this trip.", systemImage: "mappin.slash")
                    .foregroundStyle(.secondary)
                    .font(.subheadline)
            case .failed(let error):
                Label(error.message, systemImage: "exclamationmark.triangle")
                    .foregroundStyle(.secondary)
                    .font(.subheadline)
            case .loaded(let points):
                DetailRow(label: "Points", value: "\(points.count.formatted())")
                if points.compactMap(\.coordinate).count >= 2 {
                    RouteMapView(points: points)
                        .frame(height: 240)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
                }
                NavigationLink("All points") {
                    RoutePointList(points: points)
                }
            }
        }
    }

    // MARK: - Formatting

    private static func format(_ date: Date) -> String {
        date.formatted(date: .abbreviated, time: .shortened)
    }

    private static func formatDuration(_ minutes: Int) -> String {
        guard minutes >= 60 else { return "\(minutes) min" }
        let hours = minutes / 60
        let rest = minutes % 60
        return rest == 0 ? "\(hours) h" : "\(hours) h \(rest) min"
    }

    private func tint(for status: TripStatus) -> Color {
        switch status {
        case .completed: return .green
        case .inProgress, .started: return .blue
        case .scheduled: return .orange
        case .cancelled: return .red
        case .unknown: return .secondary
        }
    }
}

// MARK: - Supporting views

struct DetailRow: View {
    let label: String
    let value: String
    var monospaced = false

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer(minLength: 12)
            Group {
                if monospaced {
                    Text(value).monospaced()
                } else {
                    Text(value)
                }
            }
            .multilineTextAlignment(.trailing)
        }
        .font(.subheadline)
        .accessibilityElement(children: .combine)
    }
}

/// The plain fallback for the route: coordinates and timestamps, oldest first.
struct RoutePointList: View {
    let points: [RoutePoint]

    var body: some View {
        List(points) { point in
            VStack(alignment: .leading, spacing: 4) {
                Text("\(point.latitude), \(point.longitude)")
                    .font(.subheadline)
                    .monospaced()
                HStack(spacing: 10) {
                    Text(point.recordedAt.formatted(date: .omitted, time: .standard))
                    if let speed = point.speedKmph {
                        Label("\(speed) km/h", systemImage: "speedometer")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            .padding(.vertical, 2)
        }
        .navigationTitle("\(points.count.formatted()) points")
        .navigationBarTitleDisplayMode(.inline)
    }
}
