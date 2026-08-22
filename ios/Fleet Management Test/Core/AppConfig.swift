//
//  AppConfig.swift
//  Fleet Management Test
//

import Foundation

enum AppConfig {

    /// The backend the app talks to. Change this one line to repoint it.
    ///
    ///   Simulator, backend on this Mac   http://localhost:8000/api/v1/
    ///   Physical device                  http://<mac-lan-ip>:8000/api/v1/
    ///
    /// The Simulator shares the host's network stack, so `localhost` resolves
    /// to the Mac. Plain HTTP is allowed only in the Debug configuration - see
    /// the ATS exception in Config/Info.plist.
    static let apiBaseURL = URL(string: "http://localhost:8000/api/v1/")!

    /// How long to wait on a single request before calling it offline.
    static let requestTimeout: TimeInterval = 20
}
