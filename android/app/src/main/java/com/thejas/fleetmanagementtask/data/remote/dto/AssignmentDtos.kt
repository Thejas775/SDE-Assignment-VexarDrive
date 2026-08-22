package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssignmentDriverDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("license_number") val licenseNumber: String,
)

@Serializable
data class AssignmentDto(
    val id: String,
    val vehicle: VehicleSummaryDto,
    val driver: AssignmentDriverDto,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    val status: String,
    val notes: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
) {
    val period: String get() = "$startDate → ${endDate ?: "open ended"}"
}

@Serializable
data class AssignmentCreateRequest(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("driver_id") val driverId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    val notes: String? = null,
)

@Serializable
data class AssignmentEndRequest(
    @SerialName("end_date") val endDate: String? = null,
)
