//
//  AppEnvironment.swift
//  Fleet Management Test
//

import Combine
import Foundation

/// The composition root: the one place that decides which concrete pieces the
/// app runs with. Everything else takes its dependencies as protocols, which is
/// what lets the tests swap in a stubbed URLProtocol and an in-memory store.
@MainActor
final class AppEnvironment: ObservableObject {

    let client: APIClient
    let session: SessionController

    init(tokenStore: any TokenStoring = KeychainStore(), urlSession: URLSession? = nil) {
        let client = APIClient(tokenStore: tokenStore, session: urlSession)
        self.client = client
        self.session = SessionController(
            repository: AuthRepository(client: client, tokenStore: tokenStore),
            sessionExpired: client.sessionExpired
        )
    }
}
