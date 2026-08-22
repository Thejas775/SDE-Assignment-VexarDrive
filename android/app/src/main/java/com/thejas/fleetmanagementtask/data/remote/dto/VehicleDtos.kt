package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VehicleDto(
    val id: String,
    @SerialName("registration_number") val registrationNumber: String,
    @SerialName("vehicle_type") val vehicleType: String,
    val make: String,
    val model: String,
    val year: Int,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("current_mileage") val currentMileage: Int,
    val status: String,
    @SerialName("insurance_expiry") val insuranceExpiry: String,
    @SerialName("registration_expiry") val registrationExpiry: String,
    @SerialName("insurance_expiring_soon") val insuranceExpiringSoon: Boolean = false,
    @SerialName("registration_expiring_soon") val registrationExpiringSoon: Boolean = false,
) {
    val title: String get() = registrationNumber
    val subtitle: String get() = "$make $model · $year"
    val needsAttention: Boolean get() = insuranceExpiringSoon || registrationExpiringSoon
}

@Serializable
data class VehicleCreateRequest(
    @SerialName("registration_number") val registrationNumber: String,
    @SerialName("vehicle_type") val vehicleType: String,
    val make: String,
    val model: String,
    val year: Int,
    @SerialName("fuel_type") val fuelType: String,
    @SerialName("current_mileage") val currentMileage: Int = 0,
    @SerialName("insurance_expiry") val insuranceExpiry: String,
    @SerialName("registration_expiry") val registrationExpiry: String,
)

@Serializable
data class VehicleUpdateRequest(
    @SerialName("registration_number") val registrationNumber: String? = null,
    @SerialName("vehicle_type") val vehicleType: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    @SerialName("fuel_type") val fuelType: String? = null,
    @SerialName("current_mileage") val currentMileage: Int? = null,
    val status: String? = null,
    @SerialName("insurance_expiry") val insuranceExpiry: String? = null,
    @SerialName("registration_expiry") val registrationExpiry: String? = null,
)
