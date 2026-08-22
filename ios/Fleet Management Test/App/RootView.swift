//
//  RootView.swift
//  Fleet Management Test
//

import SwiftUI

/// Decides Login vs the signed-in app, and nothing else.
struct RootView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @ObservedObject var session: SessionController

    var body: some View {
        Group {
            switch session.phase {
            case .launching:
                ProgressView()
                    .controlSize(.large)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(.systemGroupedBackground))

            case .signedOut:
                LoginView(session: session)

            case .signedIn:
                SignedInTabs()
            }
        }
        // A cross-fade rather than a hard cut, so an expiring session does not
        // feel like a crash.
        .animation(.easeInOut(duration: 0.2), value: session.phase)
        .task {
            // Reading the keychain is the first thing that happens; until it
            // finishes the app shows the launching spinner, never Login.
            if session.phase == .launching { session.restore() }
        }
    }
}

/// Vehicles and Trips. The trip list exists to reach Trip Details, which is the
/// screen the brief asks for; a trip id could be handed in directly instead.
private struct SignedInTabs: View {

    @EnvironmentObject private var environment: AppEnvironment

    var body: some View {
        TabView {
            NavigationStack {
                VehicleListView(repository: environment.vehicles)
            }
            .tabItem { Label("Vehicles", systemImage: "truck.box.fill") }

            NavigationStack {
                TripListView(repository: environment.trips)
            }
            .tabItem { Label("Trips", systemImage: "map.fill") }
        }
    }
}
