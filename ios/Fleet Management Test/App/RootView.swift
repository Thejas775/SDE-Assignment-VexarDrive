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
                NavigationStack {
                    VehicleListView(repository: environment.vehicles)
                }
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
