package com.thejas.fleetmanagementtask.core

/** Mirrors the enums the API accepts; the wire value is always the name. */
object FleetEnums {
    val vehicleStatuses = listOf(
        "AVAILABLE", "ON_TRIP", "IN_MAINTENANCE", "INACTIVE",
    )
    val vehicleTypes = listOf(
        "TRUCK", "VAN", "CAR", "PICKUP", "BUS", "TWO_WHEELER", "TRAILER",
    )
    val driverStatuses = listOf("ACTIVE", "SUSPENDED", "INACTIVE")

    val fuelTypes = listOf(
        "PETROL", "DIESEL", "CNG", "LPG", "ELECTRIC", "HYBRID",
    )

    /** ON_TRIP is owned by the trip workflow, so it is not offered for editing. */
    val editableStatuses = listOf("AVAILABLE", "IN_MAINTENANCE", "INACTIVE")

    fun label(value: String): String =
        value.split("_").joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
}
