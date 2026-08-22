//
//  AuthRepository.swift
//  Fleet Management Test
//

import Foundation

protocol AuthRepositoryProtocol: Sendable {
    /// Signs in and persists the session. Returns the signed-in user.
    func login(email: String, password: String) async throws -> User

    /// Creates an account and signs straight in with it.
    func register(_ draft: RegisterRequest) async throws -> User

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

    /// `POST /auth/register` answers 201 with the user but no tokens, so the
    /// session only exists after the login that follows.
    func register(_ draft: RegisterRequest) async throws -> User {
        let normalised = RegisterRequest(
            email: draft.email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            password: draft.password,
            fullName: draft.fullName.trimmingCharacters(in: .whitespacesAndNewlines),
            phoneNumber: draft.phoneNumber?.trimmingCharacters(in: .whitespacesAndNewlines),
            role: draft.role,
            licenseNumber: draft.licenseNumber?.trimmingCharacters(in: .whitespacesAndNewlines),
            licenseExpiry: draft.licenseExpiry
        )

        _ = try await client.send(.register(normalised), as: User.self)
        return try await login(email: normalised.email, password: normalised.password)
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
