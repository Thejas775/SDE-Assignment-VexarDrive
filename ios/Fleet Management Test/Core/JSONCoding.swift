//
//  JSONCoding.swift
//  Fleet Management Test
//

import Foundation

/// The single decoder/encoder pair the whole app uses, so no call site can
/// accidentally decode with different rules than the tests do.
enum JSONCoding {

    static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let raw = try container.decode(String.self)
            guard let date = APIDate.parse(raw) else {
                throw DecodingError.dataCorruptedError(
                    in: container,
                    debugDescription: "Unrecognised date format: \(raw)"
                )
            }
            return date
        }
        return decoder
    }()

    static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return encoder
    }()
}

/// The API sends dates in four shapes and a single `.iso8601` strategy only
/// handles one of them:
///
///   - `2026-08-22T17:55:17.478250Z` - most timestamps, fractional seconds
///   - `2026-08-22T17:25:17Z`        - some, e.g. `recorded_at`, without
///   - `2026-08-22T22:25:49.364830`  - no zone at all, from /auth/register
///   - `2029-09-05`                  - plain dates, e.g. `insurance_expiry`
///
/// Formatters are held as statics because building a `DateFormatter` costs
/// roughly a millisecond and a route response can carry 1000 points.
enum APIDate {

    static func parse(_ raw: String) -> Date? {
        if let date = fractionalSeconds.date(from: raw)
            ?? wholeSeconds.date(from: raw)
            ?? plainDay.date(from: raw) {
            return date
        }

        // /auth/register answers with a naive timestamp - no Z, no offset -
        // while every other endpoint qualifies it. The backend stores UTC, so
        // saying so explicitly lets the ISO parsers above handle it, whatever
        // number of fractional digits it carries.
        guard isNaiveTimestamp(raw) else { return nil }
        let qualified = raw + "Z"
        return fractionalSeconds.date(from: qualified) ?? wholeSeconds.date(from: qualified)
    }

    /// True for "2026-08-22T22:25:49.364830": a date and time, but nothing
    /// saying which zone it is in.
    private static func isNaiveTimestamp(_ raw: String) -> Bool {
        let parts = raw.split(separator: "T", maxSplits: 1)
        guard parts.count == 2 else { return false }
        let time = parts[1]
        return !time.contains("Z") && !time.contains("+") && !time.contains("-")
    }

    private static let fractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let wholeSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    private static let plainDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
