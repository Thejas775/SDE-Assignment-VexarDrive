//
//  APIEnum.swift
//  Fleet Management Test
//

import Foundation

/// A string-backed enum coming from the API.
///
/// The backend owns these vocabularies and may grow a new case at any time -
/// a new vehicle status, a new fuel type. Decoding must not throw when that
/// happens, so an unrecognised raw value lands on `unknown` instead.
protocol APIEnum: RawRepresentable, Codable, Hashable, Sendable, CaseIterable
where RawValue == String {
    static var unknown: Self { get }
}

extension APIEnum {
    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = Self(rawValue: raw) ?? .unknown
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    /// The cases worth showing in a picker. `unknown` is a decoding fallback,
    /// not something a user can meaningfully choose.
    static var selectableCases: [Self] { allCases.filter { $0 != .unknown } }
}
