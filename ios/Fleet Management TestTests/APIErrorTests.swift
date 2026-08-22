//
//  APIErrorTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

final class APIErrorTests: XCTestCase {

    func testBusinessErrorMessageIsKeptVerbatim() {
        let error = APIError.from(
            status: 401,
            data: Data(#"{"error_message":"Incorrect email or password"}"#.utf8)
        )
        XCTAssertEqual(error.code, 401)
        XCTAssertEqual(error.message, "Incorrect email or password")
        XCTAssertTrue(error.isUnauthorized)
        XCTAssertTrue(error.fieldErrors.isEmpty)
    }

    func testValidationErrorAttachesToTheRightField() {
        let error = APIError.from(status: 422, data: Data("""
        {"detail":[{"type":"string_too_short","loc":["body","password"],
        "msg":"String should have at least 8 characters"}]}
        """.utf8))
        XCTAssertTrue(error.isValidation)
        XCTAssertEqual(error.fieldErrors["password"], "String should have at least 8 characters")
    }

    func testValidationErrorWithSeveralFields() {
        let error = APIError.from(status: 422, data: Data("""
        {"detail":[{"loc":["body","email"],"msg":"not an email"},
                   {"loc":["body","password"],"msg":"too short"}]}
        """.utf8))
        XCTAssertEqual(error.fieldErrors.count, 2)
        XCTAssertEqual(error.fieldErrors["email"], "not an email")
        XCTAssertEqual(error.fieldErrors["password"], "too short")
    }

    /// FastAPI puts array indices in `loc`, so it is not a [String] on the wire.
    func testValidationLocWithAnIntegerIndex() {
        let error = APIError.from(status: 422, data: Data("""
        {"detail":[{"loc":["body","items",0,"name"],"msg":"required"}]}
        """.utf8))
        XCTAssertEqual(error.fieldErrors["name"], "required")
    }

    /// FastAPI's own dependencies answer before the app's handlers run.
    func testBareDetailString() {
        let error = APIError.from(status: 401, data: Data(#"{"detail":"Not authenticated"}"#.utf8))
        XCTAssertEqual(error.message, "Not authenticated")
    }

    func testEmptyBodyFallsBackToAStatusMessage() {
        let error = APIError.from(status: 409, data: Data())
        XCTAssertEqual(error.code, 409)
        XCTAssertFalse(error.message.isEmpty)
    }

    /// A proxy in front of the app can answer with HTML.
    func testHTMLBodyDoesNotCrash() {
        let error = APIError.from(status: 500, data: Data("<html>502 Bad Gateway</html>".utf8))
        XCTAssertEqual(error.code, 500)
        XCTAssertTrue(error.isRetryable)
    }

    func testOnlyTransientFailuresAreRetryable() {
        XCTAssertTrue(APIError.offline().isRetryable)
        XCTAssertFalse(APIError.from(status: 403, data: Data()).isRetryable)
        XCTAssertFalse(APIError.from(status: 422, data: Data()).isRetryable)
    }

    func testURLErrorMapsToOffline() {
        let error = APIError.transport(URLError(.cannotConnectToHost))
        XCTAssertEqual(error.code, APIError.offlineCode)
        XCTAssertEqual(error.message, "No connection to the server.")
    }

    func testConflictMessageIsShownVerbatim() {
        let message = "KA-01-AB-1234 is already assigned from 2026-08-17 to 2026-08-25"
        let error = APIError.from(status: 409, data: Data(#"{"error_message":"\#(message)"}"#.utf8))
        XCTAssertEqual(error.message, message)
    }
}
