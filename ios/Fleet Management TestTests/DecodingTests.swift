//
//  DecodingTests.swift
//  Fleet Management TestTests
//

import XCTest
@testable import Fleet_Management_Test

final class DecodingTests: XCTestCase {

    private let decoder = JSONCoding.decoder

    func testLoginResponseDecodes() throws {
        let response = try decoder.decode(TokenResponse.self, from: Fixtures.tokenResponse())
        XCTAssertEqual(response.expiresIn, 1800)
        XCTAssertEqual(response.user.role, .fleetManager)
        XCTAssertNil(response.user.phoneNumber)
    }

    func testVehiclePageDecodesWithPlainDayDates() throws {
        let page = try decoder.decode(Page<Vehicle>.self, from: Fixtures.vehiclePage)
        XCTAssertEqual(page.total, 13)
        XCTAssertEqual(page.pages, 13)
        XCTAssertTrue(page.hasMorePages)

        let vehicle = try XCTUnwrap(page.items.first)
        XCTAssertEqual(vehicle.currentMileage, 48596)
        XCTAssertEqual(vehicle.makeModelYear, "Tata Ace · 2022")

        // insurance_expiry is YYYY-MM-DD, not a timestamp.
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(secondsFromGMT: 0))
        let expiry = try XCTUnwrap(vehicle.insuranceExpiry)
        let parts = calendar.dateComponents([.year, .month, .day], from: expiry)
        XCTAssertEqual(parts.year, 2029)
        XCTAssertEqual(parts.month, 9)
        XCTAssertEqual(parts.day, 5)
    }

    /// Decoding these as Double throws typeMismatch; they must stay strings.
    func testStringDecimalsSurviveAsStrings() throws {
        let trip = try decoder.decode(Trip.self, from: Fixtures.completedTrip)
        XCTAssertEqual(trip.distanceKm, "346.00")
        XCTAssertEqual(trip.startLatitude, "12.971599")
        XCTAssertEqual(trip.startCoordinate?.latitude, 12.971599)
        XCTAssertEqual(trip.startOdometer, 48250)
        XCTAssertEqual(trip.endOdometer, 48596)
    }

    func testScheduledTripWithNullFieldsDecodes() throws {
        let trip = try decoder.decode(Trip.self, from: Fixtures.scheduledTrip)
        XCTAssertEqual(trip.status, .scheduled)
        XCTAssertNil(trip.actualStart)
        XCTAssertNil(trip.actualEnd)
        XCTAssertNil(trip.startOdometer)
        XCTAssertNil(trip.endOdometer)
        XCTAssertNil(trip.distanceKm)
        XCTAssertNil(trip.durationMinutes)
        XCTAssertNil(trip.startCoordinate)
        XCTAssertNil(trip.endCoordinate)
        XCTAssertNil(trip.vehicle)
        XCTAssertNil(trip.driver)
    }

    /// recorded_at has no fractional seconds, received_at does - in one object.
    func testBothTimestampShapesInOneObject() throws {
        let points = try decoder.decode([RoutePoint].self, from: Fixtures.routeArray)
        let point = try XCTUnwrap(points.first)
        XCTAssertEqual(point.speedKmph, "54.20")
        XCTAssertLessThan(point.recordedAt, point.receivedAt)
    }

    func testEmptyRouteDecodes() throws {
        XCTAssertTrue(try decoder.decode([RoutePoint].self, from: Data("[]".utf8)).isEmpty)
    }

    /// The backend may grow a status the app has never heard of.
    func testUnknownEnumValueDoesNotThrow() throws {
        let json = Data("""
        {"id":"x","registration_number":"KA-99","vehicle_type":"HOVERCRAFT","make":"A",
        "model":"B","year":2022,"fuel_type":"DIESEL","current_mileage":1,"status":"IMPOUNDED",
        "insurance_expiry":null,"registration_expiry":null,
        "created_at":"2026-08-22T21:48:43.682640Z","updated_at":"2026-08-22T21:48:43.682640Z",
        "insurance_expiring_soon":false,"registration_expiring_soon":false}
        """.utf8)
        let vehicle = try decoder.decode(Vehicle.self, from: json)
        XCTAssertEqual(vehicle.status, .unknown)
        XCTAssertEqual(vehicle.vehicleType, .unknown)
    }

    func testUnknownFallbackIsHiddenFromPickers() {
        XCTAssertEqual(VehicleStatus.selectableCases.count, 4)
        XCTAssertFalse(VehicleStatus.selectableCases.contains(.unknown))
    }

    /// The API gaining a field must not break an older build.
    func testUnknownFieldIsIgnored() throws {
        var object = try XCTUnwrap(
            JSONSerialization.jsonObject(with: Fixtures.vehiclePage) as? [String: Any]
        )
        var item = try XCTUnwrap((object["items"] as? [[String: Any]])?.first)
        item["telematics_provider"] = "acme"
        object["items"] = [item]

        let data = try JSONSerialization.data(withJSONObject: object)
        XCTAssertNoThrow(try decoder.decode(Page<Vehicle>.self, from: data))
    }

    func testRequestsAreEncodedSnakeCase() throws {
        let body = try JSONCoding.encoder.encode(RefreshRequest(refreshToken: "r1"))
        let text = try XCTUnwrap(String(data: body, encoding: .utf8))
        XCTAssertTrue(text.contains("\"refresh_token\""))
    }

    /// /auth/register answers with a naive timestamp while every other endpoint
    /// qualifies it with Z. Decoding it used to throw and break signup.
    func testNaiveTimestampFromRegisterDecodes() throws {
        let body = Data("""
        {"id":"3d165791-8683-40aa-8330-5a4cd96eede0","email":"rahul@fleet.in",
        "full_name":"Rahul Sharma","phone_number":"+919876543210","role":"DRIVER",
        "is_active":true,"created_at":"2026-08-22T22:25:49.364830"}
        """.utf8)
        let user = try decoder.decode(User.self, from: body)
        XCTAssertEqual(user.role, .driver)
        XCTAssertEqual(user.phoneNumber, "+919876543210")
    }

    func testEveryTimestampShapeTheAPIUses() {
        XCTAssertNotNil(APIDate.parse("2026-08-22T17:55:17.478250Z"))
        XCTAssertNotNil(APIDate.parse("2026-08-22T17:25:17Z"))
        XCTAssertNotNil(APIDate.parse("2026-08-22T22:25:49.364830"))
        XCTAssertNotNil(APIDate.parse("2026-08-22T22:25:49"))
        XCTAssertNotNil(APIDate.parse("2026-08-22T17:55:17.478250+05:30"))
        XCTAssertNotNil(APIDate.parse("2029-09-05"))
    }

    func testGarbageDateIsRejectedRatherThanDefaulted() {
        XCTAssertNil(APIDate.parse("not-a-date"))
    }
}
