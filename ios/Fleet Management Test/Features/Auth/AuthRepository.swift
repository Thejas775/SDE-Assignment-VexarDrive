//
//  AuthRepository.swift
//  Fleet Management Test
//

import Foundation

protocol AuthRepositoryProtocol: Sendable {
    /// Signs in and persists the session. Returns the signed-in user.
    func login(email: String, password: String) async throws -> User

    /// Ends the session. Always clears local state, even if the server call fails.
    func logout() async

    /// The persisted session, if the app was signed in when it last quit.
    func restore() -> User?
}

struct AuthRepository: AuthRepositoryProtocol {

    private let client: APIClient
    private let tokenStore: any TokenStoring

    init(client: APIClient, tokenStore: any TokenStoring) {
        self.client = client
        self.tokenStore = tokenStore
    }

    func login(email: String, password: String) async throws -> User {
        // Normalised here rather than at the call site so every caller gets it.
        let normalised = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        let response = try await client.send(
            .login(email: normalised, password: password),
            as: TokenResponse.self
        )

        let session = StoredSession(
            accessToken: response.accessToken,
            refreshToken: response.refreshToken,
            user: response.user
        )
        do {
            try tokenStore.write(session)
        } catch {
            // Signing in but failing to persist would look like success and then
            // silently sign the user out on relaunch. Better to say so now.
            throw APIError(
                code: APIError.unknownCode,
                message: (error as? LocalizedError)?.errorDescription
                    ?? "Could not save the session."
            )
        }
        return response.user
    }

    func logout() async {
        // Clear locally no matter what. A user tapping Sign out while offline
        // must still end up signed out; the server-side token simply expires.
        defer { tokenStore.clear() }

        guard let refreshToken = tokenStore.read()?.refreshToken else { return }
        _ = try? await client.send(.logout(refreshToken: refreshToken))
    }

    func restore() -> User? {
        tokenStore.read()?.user
    }
}
