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
    let vehicles: any VehicleRepositoryProtocol
    let trips: any TripRepositoryProtocol

    init(tokenStore: any TokenStoring = KeychainStore(), urlSession: URLSession? = nil) {
        #if DEBUG
        // UI tests need a known starting point: without this, a session left in
        // the keychain by an earlier test decides whether this run sees Login.
        if CommandLine.arguments.contains("-ui-testing-reset") {
            tokenStore.clear()
        }
        #endif

        let client = APIClient(tokenStore: tokenStore, session: urlSession)
        self.client = client
        self.vehicles = VehicleRepository(client: client)
        self.trips = TripRepository(client: client)
        self.session = SessionController(
            repository: AuthRepository(client: client, tokenStore: tokenStore),
            sessionExpired: client.sessionExpired
        )
    }
}
