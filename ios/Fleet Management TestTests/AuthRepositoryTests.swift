//
//  AuthRepositoryTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

final class AuthRepositoryTests: XCTestCase {

    override func tearDown() {
        MockURLProtocol.reset()
        super.tearDown()
    }

    private func makeRepository(store: InMemoryTokenStore) -> AuthRepository {
        AuthRepository(
            client: APIClient(
                baseURL: Fixtures.baseURL,
                tokenStore: store,
                session: MockURLProtocol.makeSession()
            ),
            tokenStore: store
        )
    }

    func testSuccessfulLoginStoresBothTokensAndTheUser() async throws {
        let store = InMemoryTokenStore()
        let repository = makeRepository(store: store)
        MockURLProtocol.respond { _ in
            (200, Fixtures.tokenResponse(access: "a1", refresh: "r1"))
        }

        let user = try await repository.login(email: "ops@fleet.in", password: "pass-word-1")

        XCTAssertEqual(user.email, "ops@fleet.in")
        XCTAssertEqual(store.read()?.accessToken, "a1")
        XCTAssertEqual(store.read()?.refreshToken, "r1")
        XCTAssertEqual(store.read()?.user.id, user.id)
    }

    /// The brief asks for the email to be lowercased and trimmed before sending.
    func testEmailIsNormalisedBeforeSending() async throws {
        let store = InMemoryTokenStore()
        let repository = makeRepository(store: store)
        MockURLProtocol.respond { _ in (200, Fixtures.tokenResponse()) }

        _ = try await repository.login(email: "  OPS@Fleet.IN  ", password: "pass-word-1")

        let body = try XCTUnwrap(MockURLProtocol.requests.first?.capturedBody)
        let sent = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        XCTAssertEqual(sent?["email"] as? String, "ops@fleet.in")
    }

    func testRestoreReturnsThePersistedUser() {
        let store = InMemoryTokenStore(session: Fixtures.session())
        XCTAssertEqual(makeRepository(store: store).restore()?.email, "ops@fleet.in")
    }

    func testRestoreIsNilWhenNothingIsStored() {
        XCTAssertNil(makeRepository(store: InMemoryTokenStore()).restore())
    }

    /// Signing out offline must still sign the user out locally.
    func testLogoutClearsLocalStateEvenWhenTheServerCallFails() async {
        let store = InMemoryTokenStore(session: Fixtures.session())
        let repository = AuthRepository(
            client: APIClient(
                baseURL: Fixtures.baseURL,
                tokenStore: store,
                session: OfflineURLProtocol.makeSession()
            ),
            tokenStore: store
        )

        await repository.logout()

        XCTAssertNil(store.read())
    }
}
