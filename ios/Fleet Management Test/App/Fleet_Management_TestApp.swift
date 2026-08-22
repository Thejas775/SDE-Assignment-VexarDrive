//
//  Fleet_Management_TestApp.swift
//  Fleet Management Test
//

import SwiftUI

@main
struct Fleet_Management_TestApp: App {

    @StateObject private var environment = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            RootView(session: environment.session)
                .environmentObject(environment)
                .environmentObject(environment.session)
        }
    }
}
