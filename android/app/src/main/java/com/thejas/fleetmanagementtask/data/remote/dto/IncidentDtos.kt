package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PersonSummaryDto(
    val id: String,
    @SerialName("full_name") val fullName: String,
)

@Serializable
data class IncidentDto(
    val id: String,
    val vehicle: VehicleSummaryDto,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("trip_number") val tripNumber: String? = null,
    @SerialName("reported_by") val reportedBy: PersonSummaryDto,
    @SerialName("assigned_to") val assignedTo: PersonSummaryDto? = null,
    val title: String,
    val description: String,
    val severity: String,
    val status: String,
    @SerialName("reported_at") val reportedAt: String,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("resolution_notes") val resolutionNotes: String? = null,
) {
    val isOpen: Boolean get() = status != "RESOLVED"
    val isCritical: Boolean get() = severity == "CRITICAL"
}

@Serializable
data class IncidentCreateRequest(
    @SerialName("vehicle_id") val vehicleId: String,
    @SerialName("trip_id") val tripId: String? = null,
    val title: String,
    val description: String,
    val severity: String,
)

@Serializable
data class IncidentStatusRequest(
    val status: String,
    @SerialName("resolution_notes") val resolutionNotes: String? = null,
)

@Serializable
data class IncidentAssignRequest(
    @SerialName("assigned_to_id") val assignedToId: String,
)
