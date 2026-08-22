//
//  SessionController.swift
//  Fleet Management Test
//

import Combine
import Foundation

/// Owns "is anyone signed in", which is the only piece of state the whole app
/// shares. The root view switches on `phase`.
@MainActor
final class SessionController: ObservableObject {

    enum Phase: Equatable {
        /// Reading the keychain. Brief enough to show nothing but a spinner,
        /// but distinct from signedOut so the app never flashes the Login
        /// screen at a user who is in fact signed in.
        case launching
        case signedOut
        case signedIn(User)
    }

    @Published private(set) var phase: Phase = .launching

    /// Set when the session ended on its own - an expired refresh token rather
    /// than the user tapping Sign out - so Login can explain why they are back.
    @Published var expiryNotice: String?

    private let repository: any AuthRepositoryProtocol
    private var expiryWatcher: Task<Void, Never>?

    init(repository: any AuthRepositoryProtocol, sessionExpired: AsyncStream<Void>) {
        self.repository = repository

        // The client wipes the keychain when a refresh is rejected; this is how
        // the UI hears about it, from whatever background request triggered it.
        expiryWatcher = Task { [weak self] in
            for await _ in sessionExpired {
                guard let self else { return }
                self.handleExpiry()
            }
        }
    }

    deinit { expiryWatcher?.cancel() }

    /// Called once on launch. A stored session skips Login entirely.
    func restore() {
        if let user = repository.restore() {
            phase = .signedIn(user)
        } else {
            phase = .signedOut
        }
    }

    func signIn(email: String, password: String) async throws {
        let user = try await repository.login(email: email, password: password)
        expiryNotice = nil
        phase = .signedIn(user)
    }

    func signOut() async {
        await repository.logout()
        expiryNotice = nil
        phase = .signedOut
    }

    private func handleExpiry() {
        guard phase != .signedOut else { return }
        expiryNotice = "Your session expired. Please sign in again."
        phase = .signedOut
    }
}
