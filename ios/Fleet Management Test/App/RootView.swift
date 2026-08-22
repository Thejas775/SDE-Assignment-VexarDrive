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

            case .signedIn(let user):
                SignedInView(user: user)
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

/// Placeholder for the signed-in half of the app. The vehicle list replaces
/// this in the next step.
private struct SignedInView: View {
    let user: User

    @EnvironmentObject private var session: SessionController

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.fill")
                .font(.largeTitle)
                .foregroundStyle(.green)
            Text("Signed in as \(user.fullName)")
                .font(.headline)
            Text(user.role.displayName)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("Sign out") {
                Task { await session.signOut() }
            }
            .padding(.top, 8)
        }
    }
}
