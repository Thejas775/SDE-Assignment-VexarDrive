package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleCountsDto(
    val total: Int,
    val available: Int,
    @SerialName("on_trip") val onTrip: Int,
    @SerialName("in_maintenance") val inMaintenance: Int,
    val inactive: Int,
)

@Serializable
data class TripCountsDto(
    val active: Int,
    @SerialName("scheduled_today") val scheduledToday: Int,
    @SerialName("completed_today") val completedToday: Int,
)

@Serializable
data class ExpiringDocumentsDto(
    val insurance: Int,
    val registration: Int,
    @SerialName("driver_license") val driverLicense: Int,
) {
    val total: Int get() = insurance + registration + driverLicense
}

@Serializable
data class RecentIncidentDto(
    val id: String,
    val title: String,
    val severity: String,
    val status: String,
    @SerialName("registration_number") val registrationNumber: String,
    @SerialName("reported_at") val reportedAt: String,
)

@Serializable
data class DashboardDto(
    val vehicles: VehicleCountsDto,
    val trips: TripCountsDto,
    @SerialName("drivers_active") val driversActive: Int,
    // Decimal arrives as a string: the API refuses to round money or distance
    // through a float on the way out.
    @SerialName("distance_today_km") val distanceTodayKm: String,
    @SerialName("maintenance_due") val maintenanceDue: Int,
    @SerialName("open_incidents") val openIncidents: Int,
    @SerialName("expiring_documents") val expiringDocuments: ExpiringDocumentsDto,
    @SerialName("recent_incidents") val recentIncidents: List<RecentIncidentDto>,
)
