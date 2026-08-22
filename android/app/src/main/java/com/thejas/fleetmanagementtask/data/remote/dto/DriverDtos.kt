package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DriverDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("license_number") val licenseNumber: String,
    @SerialName("license_expiry") val licenseExpiry: String,
    val status: String,
    @SerialName("can_login") val canLogin: Boolean,
    @SerialName("assigned_vehicle") val assignedVehicle: VehicleSummaryDto? = null,
    @SerialName("license_expiring_soon") val licenseExpiringSoon: Boolean = false,
    @SerialName("license_expired") val licenseExpired: Boolean = false,
) {
    val needsAttention: Boolean get() = licenseExpiringSoon || licenseExpired
}

@Serializable
data class DriverCreateRequest(
    val email: String,
    val password: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("license_number") val licenseNumber: String,
    @SerialName("license_expiry") val licenseExpiry: String,
)

@Serializable
data class DriverUpdateRequest(
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("license_number") val licenseNumber: String? = null,
    @SerialName("license_expiry") val licenseExpiry: String? = null,
)

/** Distances here are JSON numbers, unlike the Decimal strings elsewhere. */
@Serializable
data class DriverPerformanceDto(
    @SerialName("driver_id") val driverId: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("total_trips") val totalTrips: Int,
    @SerialName("completed_trips") val completedTrips: Int,
    @SerialName("cancelled_trips") val cancelledTrips: Int,
    @SerialName("total_distance_km") val totalDistanceKm: Double,
    @SerialName("average_trip_duration_minutes") val averageTripDurationMinutes: Int? = null,
    @SerialName("average_distance_km") val averageDistanceKm: Double? = null,
    @SerialName("incidents_reported") val incidentsReported: Int,
)
