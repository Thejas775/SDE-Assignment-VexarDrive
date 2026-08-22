//
//  KeychainStore.swift
//  Fleet Management Test
//

import Foundation
import Security

/// Where the session lives between launches.
///
/// Not UserDefaults: that is a plist inside the app container, which comes out
/// of an unencrypted device backup and is readable outright on a jailbroken
/// device. Tokens go in the Keychain as `kSecClassGenericPassword` with
/// `kSecAttrAccessibleAfterFirstUnlock` - available to background work after
/// the first unlock following a reboot, but never while the device is locked
/// from cold, and never synced to iCloud.
protocol TokenStoring: Sendable {
    func read() -> StoredSession?
    func write(_ session: StoredSession) throws
    func clear()
}

/// The two tokens, persisted together so they can never drift apart - the
/// refresh rotation replaces both at once.
struct StoredSession: Codable, Sendable, Equatable {
    let accessToken: String
    let refreshToken: String
}

struct KeychainStore: TokenStoring {

    /// Distinguishes this app's item from anything else in the shared keychain.
    private let service: String
    private let account: String

    init(service: String = Bundle.main.bundleIdentifier ?? "fleet-management",
         account: String = "session") {
        self.service = service
        self.account = account
    }

    func read() -> StoredSession? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return try? JSONDecoder().decode(StoredSession.self, from: data)
    }

    func write(_ session: StoredSession) throws {
        let data = try JSONEncoder().encode(session)

        // Update in place if an item already exists; SecItemAdd would fail with
        // errSecDuplicateItem, and delete-then-add leaves a window with no token.
        let updateStatus = SecItemUpdate(
            baseQuery as CFDictionary,
            [kSecValueData as String: data] as CFDictionary
        )
        if updateStatus == errSecSuccess { return }

        guard updateStatus == errSecItemNotFound else {
            throw KeychainError(status: updateStatus)
        }

        var insert = baseQuery
        insert[kSecValueData as String] = data
        insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        let addStatus = SecItemAdd(insert as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw KeychainError(status: addStatus) }
    }

    func clear() {
        // errSecItemNotFound is the desired end state too, so nothing to report.
        SecItemDelete(baseQuery as CFDictionary)
    }

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}

struct KeychainError: Error, LocalizedError {
    let status: OSStatus

    var errorDescription: String? {
        let detail = SecCopyErrorMessageString(status, nil) as String?
        return "Could not save the session (\(detail ?? "OSStatus \(status)"))."
    }
}

/// An in-memory stand-in for tests and SwiftUI previews, where there is no
/// keychain to talk to and no reason to leave anything behind.
final class InMemoryTokenStore: TokenStoring, @unchecked Sendable {
    private let lock = NSLock()
    private var session: StoredSession?

    init(session: StoredSession? = nil) { self.session = session }

    func read() -> StoredSession? {
        lock.withLock { session }
    }

    func write(_ session: StoredSession) throws {
        lock.withLock { self.session = session }
    }

    func clear() {
        lock.withLock { session = nil }
    }
}
