package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationPingRequest(
    val latitude: String,
    val longitude: String,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("speed_kmph") val speedKmph: String? = null,
    val heading: String? = null,
    @SerialName("accuracy_m") val accuracyM: String? = null,
)

@Serializable
data class LocationBatchRequest(
    @SerialName("trip_id") val tripId: String,
    val pings: List<LocationPingRequest>,
)

@Serializable
data class LocationIngestResultDto(
    val accepted: Int,
    val duplicates: Int,
    @SerialName("trip_status") val tripStatus: String,
)
