//
//  APIClient.swift
//  Fleet Management Test
//

import Foundation

/// The single place that talks to the backend.
///
/// An `actor` on purpose: the refresh rule needs mutual exclusion. Refresh
/// tokens rotate - every successful refresh invalidates the previous one - so
/// if three requests 401 at the same moment and each fires its own refresh,
/// two of them present an already-rotated token and the user is signed out.
/// Only one refresh may be in flight; the others await its result.
actor APIClient {

    private let baseURL: URL
    private let session: URLSession
    private let tokenStore: any TokenStoring

    /// The one in-flight refresh, if any. Everything else waits on it.
    private var refreshTask: Task<StoredSession, Error>?

    /// Fires when the session is gone for good and the UI must return to Login:
    /// the refresh itself was rejected, or there was no refresh token to use.
    nonisolated let sessionExpired: AsyncStream<Void>
    private nonisolated let expiredContinuation: AsyncStream<Void>.Continuation

    init(
        baseURL: URL = AppConfig.apiBaseURL,
        tokenStore: any TokenStoring = KeychainStore(),
        session: URLSession? = nil
    ) {
        self.baseURL = baseURL
        self.tokenStore = tokenStore

        if let session {
            self.session = session
        } else {
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = AppConfig.requestTimeout
            configuration.waitsForConnectivity = false
            self.session = URLSession(configuration: configuration)
        }

        var continuation: AsyncStream<Void>.Continuation!
        self.sessionExpired = AsyncStream { continuation = $0 }
        self.expiredContinuation = continuation
    }

    // MARK: - Sending

    func send<Response: Decodable & Sendable>(
        _ endpoint: Endpoint,
        as type: Response.Type = Response.self
    ) async throws -> Response {
        let data = try await data(for: endpoint)
        if data.isEmpty, let empty = EmptyResponse() as? Response { return empty }
        do {
            return try JSONCoding.decoder.decode(Response.self, from: data)
        } catch {
            throw APIError.decoding(error)
        }
    }

    /// For calls whose body the app does not care about.
    @discardableResult
    func send(_ endpoint: Endpoint) async throws -> Data {
        try await data(for: endpoint)
    }

    // MARK: - The 401 dance

    private func data(for endpoint: Endpoint) async throws -> Data {
        let tokenUsed = endpoint.requiresAuth ? tokenStore.read()?.accessToken : nil
        let (data, response) = try await perform(endpoint, accessToken: tokenUsed)

        guard response.statusCode == 401, endpoint.requiresAuth else {
            return try unwrap(data: data, response: response)
        }

        // Exactly one retry. A refresh that succeeds but still yields 401 means
        // something else is wrong, and looping would just hammer the server.
        let renewed = try await refreshedSession(replacing: tokenUsed)
        let (retryData, retryResponse) = try await perform(
            endpoint, accessToken: renewed.accessToken
        )
        return try unwrap(data: retryData, response: retryResponse)
    }

    /// Returns a session whose access token is newer than `staleToken`,
    /// refreshing at most once across all concurrent callers.
    private func refreshedSession(replacing staleToken: String?) async throws -> StoredSession {
        // Someone else is already refreshing - wait for their result rather than
        // starting a second one against a token they are about to rotate away.
        if let inFlight = refreshTask {
            return try await inFlight.value
        }

        // Or someone else finished while this request was awaiting its 401,
        // in which case the stored token is already good and no call is needed.
        if let current = tokenStore.read(), current.accessToken != staleToken {
            return current
        }

        guard let refreshToken = tokenStore.read()?.refreshToken else {
            expireSession()
            throw APIError(code: 401, message: "Your session has expired. Please sign in again.")
        }

        let task = Task<StoredSession, Error> { [self] in
            try await performRefresh(using: refreshToken)
        }
        refreshTask = task

        defer { refreshTask = nil }
        return try await task.value
    }

    private func performRefresh(using refreshToken: String) async throws -> StoredSession {
        let (data, response) = try await perform(
            .refresh(refreshToken: refreshToken), accessToken: nil
        )

        guard (200...299).contains(response.statusCode) else {
            // The refresh token itself was rejected - rotated away, revoked or
            // expired. Nothing left to try.
            expireSession()
            throw APIError.from(status: response.statusCode, data: data)
        }

        let tokens: TokenResponse
        do {
            tokens = try JSONCoding.decoder.decode(TokenResponse.self, from: data)
        } catch {
            expireSession()
            throw APIError.decoding(error)
        }

        // Persist immediately. The old refresh token is already dead server-side;
        // losing the new one here would sign the user out on the next refresh.
        let renewed = StoredSession(
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken
        )
        try? tokenStore.write(renewed)
        return renewed
    }

    private func expireSession() {
        tokenStore.clear()
        expiredContinuation.yield()
    }

    // MARK: - Transport

    private func perform(
        _ endpoint: Endpoint,
        accessToken: String?
    ) async throws -> (Data, HTTPURLResponse) {
        let request = try makeRequest(endpoint, accessToken: accessToken)
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else {
                throw APIError(code: APIError.unknownCode, message: "Malformed server response.")
            }
            return (data, http)
        } catch let error as APIError {
            throw error
        } catch let error as URLError {
            throw APIError.transport(error)
        } catch {
            throw APIError(code: APIError.unknownCode, message: error.localizedDescription)
        }
    }

    private func unwrap(data: Data, response: HTTPURLResponse) throws -> Data {
        guard (200...299).contains(response.statusCode) else {
            throw APIError.from(status: response.statusCode, data: data)
        }
        return data
    }

    private func makeRequest(_ endpoint: Endpoint, accessToken: String?) throws -> URLRequest {
        guard var components = URLComponents(
            url: baseURL.appendingPathComponent(endpoint.path),
            resolvingAgainstBaseURL: false
        ) else {
            throw APIError(code: APIError.unknownCode, message: "Could not build the request URL.")
        }
        if !endpoint.query.isEmpty { components.queryItems = endpoint.query }

        guard let url = components.url else {
            throw APIError(code: APIError.unknownCode, message: "Could not build the request URL.")
        }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let body = endpoint.body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            do {
                request.httpBody = try JSONCoding.encoder.encode(body)
            } catch {
                throw APIError(code: APIError.unknownCode, message: "Could not encode the request.")
            }
        }

        if endpoint.requiresAuth, let accessToken {
            request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        }

        return request
    }
}

/// Stands in for a response body the app does not need to read.
struct EmptyResponse: Decodable, Sendable {}
