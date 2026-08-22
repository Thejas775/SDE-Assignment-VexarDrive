//
//  KeychainStoreTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

/// Exercises the real keychain, under a service name unique to this test run so
/// it cannot collide with the app's own item.
final class KeychainStoreTests: XCTestCase {

    private var store: KeychainStore!

    override func setUp() {
        super.setUp()
        store = KeychainStore(service: "fleet-tests-\(UUID().uuidString)", account: "session")
    }

    override func tearDown() {
        store.clear()
        store = nil
        super.tearDown()
    }

    func testReadIsNilBeforeAnythingIsWritten() {
        XCTAssertNil(store.read())
    }

    func testWriteThenReadRoundTrips() throws {
        let session = Fixtures.session(access: "a1", refresh: "r1")
        try store.write(session)

        let restored = store.read()
        XCTAssertEqual(restored?.accessToken, "a1")
        XCTAssertEqual(restored?.refreshToken, "r1")
        XCTAssertEqual(restored?.user.email, "ops@fleet.in")
    }

    /// The rotation overwrites an existing item rather than failing with
    /// errSecDuplicateItem.
    func testWritingTwiceUpdatesInPlace() throws {
        try store.write(Fixtures.session(access: "a1", refresh: "r1"))
        try store.write(Fixtures.session(access: "a2", refresh: "r2"))

        XCTAssertEqual(store.read()?.accessToken, "a2")
        XCTAssertEqual(store.read()?.refreshToken, "r2")
    }

    func testClearRemovesTheSession() throws {
        try store.write(Fixtures.session())
        store.clear()
        XCTAssertNil(store.read())
    }

    func testClearingTwiceIsHarmless() {
        store.clear()
        store.clear()
        XCTAssertNil(store.read())
    }

    func testTwoServicesDoNotSeeEachOther() throws {
        let other = KeychainStore(service: "fleet-tests-other-\(UUID().uuidString)",
                                  account: "session")
        defer { other.clear() }

        try store.write(Fixtures.session(access: "mine", refresh: "r"))

        XCTAssertNil(other.read())
        XCTAssertEqual(store.read()?.accessToken, "mine")
    }
}
