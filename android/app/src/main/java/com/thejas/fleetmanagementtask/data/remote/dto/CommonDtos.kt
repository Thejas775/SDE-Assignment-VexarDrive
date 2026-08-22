package com.thejas.fleetmanagementtask.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Envelope shared by every list endpoint. */
@Serializable
data class PageDto<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val pages: Int,
) {
    val hasMore: Boolean get() = page < pages
}

/** Vehicle summary embedded in driver, assignment and trip payloads. */
@Serializable
data class VehicleSummaryDto(
    val id: String,
    @SerialName("registration_number") val registrationNumber: String,
    val make: String,
    val model: String,
) {
    val label: String get() = "$registrationNumber · $make $model"
}
