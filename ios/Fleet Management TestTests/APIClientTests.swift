//
//  APIClientTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

final class APIClientTests: XCTestCase {

    override func tearDown() {
        MockURLProtocol.reset()
        super.tearDown()
    }

    private func makeClient(
        store: InMemoryTokenStore
    ) -> APIClient {
        APIClient(
            baseURL: Fixtures.baseURL,
            tokenStore: store,
            session: MockURLProtocol.makeSession()
        )
    }

    // MARK: - The refresh rule

    /// Refresh tokens rotate, so three simultaneous 401s must produce exactly
    /// one refresh. Two extra refreshes would present an already-rotated token
    /// and sign the user out.
    func testConcurrent401sTriggerASingleRefresh() async throws {
        let store = InMemoryTokenStore(session: Fixtures.session(access: "old", refresh: "r1"))
        let client = makeClient(store: store)

        let refreshes = Counter()
        MockURLProtocol.respond { request in
            if request.url?.path.hasSuffix("/auth/refresh") == true {
                refreshes.increment()
                // Long enough that the other two are definitely waiting on it.
                Thread.sleep(forTimeInterval: 0.15)
                return (200, Fixtures.tokenResponse(access: "new", refresh: "r2"))
            }
            let authorised = request.value(forHTTPHeaderField: "Authorization") == "Bearer new"
            return authorised
                ? (200, Fixtures.vehiclePage)
                : (401, Data(#"{"error_message":"Token expired"}"#.utf8))
        }

        let results = await withTaskGroup(of: Bool.self, returning: [Bool].self) { group in
            for _ in 0..<3 {
                group.addTask {
                    let page = try? await client.send(
                        .vehicles(page: 1, pageSize: 20), as: Page<Vehicle>.self
                    )
                    return page != nil
                }
            }
            var collected: [Bool] = []
            for await value in group { collected.append(value) }
            return collected
        }

        XCTAssertEqual(results, [true, true, true], "all three requests should be replayed")
        XCTAssertEqual(refreshes.value, 1, "only one refresh may run")
        XCTAssertEqual(store.read()?.accessToken, "new")
        XCTAssertEqual(store.read()?.refreshToken, "r2", "the rotated token must be persisted")
    }

    func testRefreshCarriesNoAccessToken() async throws {
        let store = InMemoryTokenStore(session: Fixtures.session(access: "old", refresh: "r1"))
        let client = makeClient(store: store)

        MockURLProtocol.respond { request in
            request.url?.path.hasSuffix("/auth/refresh") == true
                ? (200, Fixtures.tokenResponse(access: "new", refresh: "r2"))
                : (request.value(forHTTPHeaderField: "Authorization") == "Bearer new"
                    ? (200, Fixtures.vehiclePage)
                    : (401, Data(#"{"error_message":"expired"}"#.utf8)))
        }

        _ = try await client.send(.vehicles(page: 1, pageSize: 20), as: Page<Vehicle>.self)

        let refreshRequests = MockURLProtocol.requests(matching: "/auth/refresh")
        XCTAssertFalse(refreshRequests.isEmpty)
        for request in refreshRequests {
            XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
        }
    }

    /// A refresh that succeeds but whose replay still 401s must not loop.
    func testRetriesAtMostOnce() async {
        let store = InMemoryTokenStore(session: Fixtures.session(access: "old", refresh: "r1"))
        let client = makeClient(store: store)

        MockURLProtocol.respond { request in
            request.url?.path.hasSuffix("/auth/refresh") == true
                ? (200, Fixtures.tokenResponse(access: "new", refresh: "r2"))
                : (401, Data(#"{"error_message":"Still no"}"#.utf8))
        }

        do {
            _ = try await client.send(.vehicles(page: 1, pageSize: 20), as: Page<Vehicle>.self)
            XCTFail("expected the second 401 to surface")
        } catch let error as APIError {
            XCTAssertEqual(error.code, 401)
        } catch {
            XCTFail("unexpected error: \(error)")
        }

        let attempts = MockURLProtocol.requests(matching: "/vehicles")
        XCTAssertEqual(attempts.count, 2, "one original attempt plus exactly one replay")
        XCTAssertEqual(attempts.last?.value(forHTTPHeaderField: "Authorization"), "Bearer new")
    }

    func testRejectedRefreshClearsTheSessionAndSignals() async {
        let store = InMemoryTokenStore(session: Fixtures.session(access: "old", refresh: "dead"))
        let client = makeClient(store: store)

        let expired = expectation(description: "sessionExpired fires")
        let watcher = Task {
            for await _ in client.sessionExpired {
                expired.fulfill()
                return
            }
        }

        MockURLProtocol.respond { request in
            request.url?.path.hasSuffix("/auth/refresh") == true
                ? (401, Data(#"{"error_message":"Refresh token revoked"}"#.utf8))
                : (401, Data(#"{"error_message":"Token expired"}"#.utf8))
        }

        do {
            _ = try await client.send(.vehicles(page: 1, pageSize: 20), as: Page<Vehicle>.self)
            XCTFail("expected a failure")
        } catch let error as APIError {
            XCTAssertEqual(error.message, "Refresh token revoked")
        } catch {
            XCTFail("unexpected error: \(error)")
        }

        await fulfillment(of: [expired], timeout: 2)
        watcher.cancel()
        XCTAssertNil(store.read(), "the keychain must be wiped")
    }

    // MARK: - Login

    func testLoginCarriesNoStaleAccessToken() async {
        let store = InMemoryTokenStore(session: Fixtures.session(access: "stale", refresh: "r"))
        let client = makeClient(store: store)
        MockURLProtocol.respond { _ in (200, Fixtures.tokenResponse()) }

        _ = try? await client.send(
            .login(email: "ops@fleet.in", password: "pass-word-1"), as: TokenResponse.self
        )

        let request = MockURLProtocol.requests.first
        XCTAssertNil(request?.value(forHTTPHeaderField: "Authorization"))
        XCTAssertEqual(request?.url?.path, "/api/v1/auth/login")
    }

    func testFailedLoginDoesNotAttemptARefresh() async {
        let store = InMemoryTokenStore(session: Fixtures.session())
        let client = makeClient(store: store)
        MockURLProtocol.respond { _ in
            (401, Data(#"{"error_message":"Incorrect email or password"}"#.utf8))
        }

        do {
            _ = try await client.send(
                .login(email: "a@b.co", password: "x"), as: TokenResponse.self
            )
            XCTFail("expected a 401")
        } catch let error as APIError {
            XCTAssertEqual(error.message, "Incorrect email or password")
        } catch {
            XCTFail("unexpected error: \(error)")
        }

        XCTAssertTrue(MockURLProtocol.requests(matching: "/auth/refresh").isEmpty)
    }

    // MARK: - Query building

    func testFiltersAreSentAndEmptyOnesOmitted() async {
        let store = InMemoryTokenStore(session: Fixtures.session())
        let client = makeClient(store: store)
        MockURLProtocol.respond { _ in (200, Fixtures.vehiclePage) }

        _ = try? await client.send(
            .vehicles(page: 2, pageSize: 20, search: "tata", status: .available),
            as: Page<Vehicle>.self
        )
        let query = MockURLProtocol.requests.first?.url?.query ?? ""
        XCTAssertTrue(query.contains("page=2"))
        XCTAssertTrue(query.contains("search=tata"))
        XCTAssertTrue(query.contains("status=AVAILABLE"))

        MockURLProtocol.respond { _ in (200, Fixtures.vehiclePage) }
        _ = try? await client.send(
            .vehicles(page: 1, pageSize: 20, search: "", status: nil), as: Page<Vehicle>.self
        )
        let bare = MockURLProtocol.requests.first?.url?.query ?? ""
        XCTAssertFalse(bare.contains("search"), "an empty search must be omitted, not sent blank")
        XCTAssertFalse(bare.contains("status"))
    }

    // MARK: - Offline

    func testBackendDownSurfacesAsOfflineRatherThanHanging() async {
        let client = APIClient(
            baseURL: Fixtures.baseURL,
            tokenStore: InMemoryTokenStore(),
            session: OfflineURLProtocol.makeSession()
        )

        do {
            _ = try await client.send(
                .login(email: "a@b.co", password: "x"), as: TokenResponse.self
            )
            XCTFail("expected an offline failure")
        } catch let error as APIError {
            XCTAssertEqual(error.code, APIError.offlineCode)
            XCTAssertEqual(error.message, "No connection to the server.")
            XCTAssertTrue(error.isRetryable)
        } catch {
            XCTFail("unexpected error: \(error)")
        }
    }
}

/// Thread-safe tally for assertions made from the URLProtocol's thread.
final class Counter: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0
    func increment() { lock.withLock { count += 1 } }
    var value: Int { lock.withLock { count } }
}
