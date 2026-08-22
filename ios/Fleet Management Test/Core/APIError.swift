//
//  APIError.swift
//  Fleet Management Test
//

import Foundation

/// Every failure the app can show a user, flattened into one type.
///
/// The backend speaks two error dialects and the app must handle both:
///
///   business failure   { "error_message": "Incorrect email or password" }
///   validation (422)   { "detail": [ { "loc": ["body","password"], "msg": "..." } ] }
///
/// Transport failures (offline, timeout) and decoding failures are folded in
/// here too, so a call site only ever catches one error type.
struct APIError: Error, Equatable {

    /// HTTP status, or one of the synthetic codes below for client-side failures.
    let code: Int

    /// Safe to show verbatim - the backend writes these to be user-facing
    /// ("KA-01-AB-1234 is already assigned from 2026-08-17 to 2026-08-25").
    let message: String

    /// Field name -> message, from the last element of each `loc` on a 422.
    /// Lets the view attach "String should have at least 8 characters" to the
    /// password field rather than dumping it in a banner.
    let fieldErrors: [String: String]

    init(code: Int, message: String, fieldErrors: [String: String] = [:]) {
        self.code = code
        self.message = message
        self.fieldErrors = fieldErrors
    }

    // MARK: - Synthetic codes

    /// No route to the server: offline, timeout, connection refused.
    static let offlineCode = -1009
    /// The response body was not what the contract promised.
    static let decodingCode = -2
    /// Something went wrong before a request was even made.
    static let unknownCode = -3

    // MARK: - Classification

    var isUnauthorized: Bool { code == 401 }
    var isForbidden: Bool { code == 403 }
    var isNotFound: Bool { code == 404 }
    var isValidation: Bool { code == 422 }

    /// Whether offering the user a Retry button makes sense. A 403 will fail
    /// again identically; a dropped connection may not.
    var isRetryable: Bool { code == Self.offlineCode || (500...599).contains(code) }

    // MARK: - Construction

    static func offline(_ message: String = "No connection to the server.") -> APIError {
        APIError(code: offlineCode, message: message)
    }

    static func decoding(_ underlying: Error) -> APIError {
        APIError(
            code: decodingCode,
            message: "The server sent something the app could not read.\n\(underlying)"
        )
    }

    /// Maps a `URLError` to either the offline case or a generic transport failure.
    static func transport(_ error: URLError) -> APIError {
        switch error.code {
        case .notConnectedToInternet, .networkConnectionLost, .cannotConnectToHost,
             .cannotFindHost, .timedOut, .dataNotAllowed, .internationalRoamingOff,
             .secureConnectionFailed, .appTransportSecurityRequiresSecureConnection:
            return .offline()
        default:
            return APIError(code: error.errorCode, message: error.localizedDescription)
        }
    }

    /// Parses an error response body, falling back to a status-appropriate
    /// message when the body is empty or in neither known shape.
    static func from(status: Int, data: Data) -> APIError {
        if let business = try? JSONCoding.decoder.decode(BusinessErrorBody.self, from: data) {
            return APIError(code: status, message: business.errorMessage)
        }
        if let validation = try? JSONCoding.decoder.decode(ValidationErrorBody.self, from: data),
           !validation.detail.isEmpty {
            var fields: [String: String] = [:]
            for item in validation.detail {
                // loc is ["body", "password"] - the field name is the tail.
                guard let field = item.loc.last, field != "body" else { continue }
                // Keep the first message per field; later ones are usually noise.
                if fields[field] == nil { fields[field] = item.msg }
            }
            let summary = validation.detail.first?.msg ?? defaultMessage(for: status)
            return APIError(code: status, message: summary, fieldErrors: fields)
        }
        // FastAPI's own dependencies (before our handlers run) answer with a
        // bare string detail, e.g. { "detail": "Not authenticated" }.
        if let plain = try? JSONCoding.decoder.decode(PlainDetailBody.self, from: data) {
            return APIError(code: status, message: plain.detail)
        }
        return APIError(code: status, message: defaultMessage(for: status))
    }

    private static func defaultMessage(for status: Int) -> String {
        switch status {
        case 401: return "Your session has expired. Please sign in again."
        case 403: return "You do not have access to this."
        case 404: return "Not found."
        case 409: return "That conflicts with something already on the server."
        case 422: return "Some of the details are invalid."
        case 500...599: return "The server ran into a problem. Please try again."
        default: return "Something went wrong (HTTP \(status))."
        }
    }
}

// MARK: - Wire shapes

private struct BusinessErrorBody: Decodable {
    let errorMessage: String
}

private struct PlainDetailBody: Decodable {
    let detail: String
}

private struct ValidationErrorBody: Decodable {
    struct Item: Decodable {
        let type: String?
        /// FastAPI mixes strings and array indices in `loc`, so decode leniently.
        let loc: [String]
        let msg: String

        private enum CodingKeys: String, CodingKey { case type, loc, msg }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            type = try container.decodeIfPresent(String.self, forKey: .type)
            msg = try container.decode(String.self, forKey: .msg)

            var locContainer = try container.nestedUnkeyedContainer(forKey: .loc)
            var parts: [String] = []
            while !locContainer.isAtEnd {
                if let text = try? locContainer.decode(String.self) {
                    parts.append(text)
                } else if let index = try? locContainer.decode(Int.self) {
                    parts.append(String(index))
                } else {
                    _ = try? locContainer.decode(AnyCodableSkip.self)
                }
            }
            loc = parts
        }
    }

    let detail: [Item]
}

/// Consumes one value of an unknown type so an unkeyed container can advance.
private struct AnyCodableSkip: Decodable {
    init(from decoder: Decoder) throws { _ = try decoder.singleValueContainer() }
}
