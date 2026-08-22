//
//  Fixtures.swift
//  Fleet Management TestTests
//

import Foundation
@testable import Fleet_Management_Test

/// Payloads copied from the API contract in the brief - real responses captured
/// from the running backend, not invented shapes.
enum Fixtures {

    static let baseURL = URL(string: "http://localhost:8000/api/v1/")!

    static func tokenResponse(access: String = "access-1",
                              refresh: String = "refresh-1") -> Data {
        Data("""
        {"access_token":"\(access)","refresh_token":"\(refresh)","token_type":"bearer",
         "expires_in":1800,
         "user":{"id":"03c4cd3f-41e2-4083-b894-59607f41ee2e","email":"ops@fleet.in",
         "full_name":"Priya Nair","phone_number":null,"role":"FLEET_MANAGER",
         "is_active":true,"created_at":"2026-08-22T19:32:53.970198Z"}}
        """.utf8)
    }

    static let vehiclePage = Data("""
    {"items":[{"id":"b0410adb-90b9-4ac3-bb3e-70146075b3f6",
    "registration_number":"KA-01-AB-1234","vehicle_type":"TRUCK","make":"Tata","model":"Ace",
    "year":2022,"fuel_type":"DIESEL","current_mileage":48596,"status":"AVAILABLE",
    "insurance_expiry":"2029-09-05","registration_expiry":"2030-01-01",
    "created_at":"2026-08-22T21:48:43.682640Z","updated_at":"2026-08-22T21:48:45.058944Z",
    "insurance_expiring_soon":false,"registration_expiring_soon":false}],
    "total":13,"page":1,"page_size":1,"pages":13}
    """.utf8)

    static let completedTrip = Data("""
    {"id":"40a77552-6262-4a81-89f1-f2c2c5660fd0","trip_number":"TRP1007",
    "vehicle":{"id":"c3344844-881b-4891-8355-ee2429132c35",
    "registration_number":"KA-01-AB-1234","make":"Tata","model":"Ace"},
    "driver":{"id":"ffabbb1d-7c1a-4d1f-9ae4-5925704e32c5","full_name":"Rahul Sharma",
    "license_number":"KA0120230001234"},"source":"Bangalore","destination":"Chennai",
    "scheduled_start":"2026-08-23T08:00:00Z","scheduled_end":"2026-08-23T18:00:00Z",
    "status":"COMPLETED","actual_start":"2026-08-22T17:55:17.478250Z",
    "actual_end":"2026-08-22T17:55:18.051048Z","start_odometer":48250,"end_odometer":48596,
    "start_latitude":"12.971599","start_longitude":"77.594566","end_latitude":"13.082680",
    "end_longitude":"80.270721","distance_km":"346.00","notes":null,
    "created_at":"2026-08-22T21:48:45.153928Z","duration_minutes":0}
    """.utf8)

    /// The awkward one: a trip that never ran, with nine null fields.
    static let scheduledTrip = Data("""
    {"id":"11111111-2222-3333-4444-555555555555","trip_number":"TRP1008","vehicle":null,
    "driver":null,"source":"Bangalore","destination":"Mysore",
    "scheduled_start":"2026-08-24T08:00:00Z","scheduled_end":"2026-08-24T12:00:00Z",
    "status":"SCHEDULED","actual_start":null,"actual_end":null,"start_odometer":null,
    "end_odometer":null,"start_latitude":null,"start_longitude":null,"end_latitude":null,
    "end_longitude":null,"distance_km":null,"notes":null,
    "created_at":"2026-08-22T21:48:45.153928Z","duration_minutes":null}
    """.utf8)

    /// recorded_at has no fractional seconds; received_at does.
    static let routeArray = Data("""
    [{"id":"03842071-9e26-4706-bd1c-bd1d89f71f53","trip_id":"40a77552-6262-4a81-89f1-f2c2c5660fd0",
    "vehicle_id":"c3344844-881b-4891-8355-ee2429132c35","latitude":"12.970000",
    "longitude":"77.590000","speed_kmph":"54.20","heading":null,"accuracy_m":null,
    "recorded_at":"2026-08-22T17:25:17Z","received_at":"2026-08-22T17:55:17.726920Z"}]
    """.utf8)

    static let user = try! JSONCoding.decoder.decode(
        TokenResponse.self, from: tokenResponse()
    ).user

    static func session(access: String = "access-1",
                        refresh: String = "refresh-1") -> StoredSession {
        StoredSession(accessToken: access, refreshToken: refresh, user: user)
    }

    static func vehicle(index: Int) -> Vehicle {
        let json = """
        {"id":"v\(index)","registration_number":"KA-01-AB-\(1000 + index)",
        "vehicle_type":"TRUCK","make":"Tata","model":"Ace","year":2022,"fuel_type":"DIESEL",
        "current_mileage":\(index),"status":"AVAILABLE","insurance_expiry":null,
        "registration_expiry":null,"created_at":"2026-08-22T21:48:43.682640Z",
        "updated_at":"2026-08-22T21:48:43.682640Z","insurance_expiring_soon":false,
        "registration_expiring_soon":false}
        """
        return try! JSONCoding.decoder.decode(Vehicle.self, from: Data(json.utf8))
    }
}
