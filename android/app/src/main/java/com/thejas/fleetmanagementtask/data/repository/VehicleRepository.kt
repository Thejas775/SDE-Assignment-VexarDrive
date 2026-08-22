package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiError
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.ApiErrorParser
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleUpdateRequest
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

data class VehicleFilters(
    val search: String? = null,
    val status: String? = null,
    val vehicleType: String? = null,
    val fuelType: String? = null,
    val expiringDocuments: Boolean = false,
) {
    val isActive: Boolean
        get() = !search.isNullOrBlank() || status != null || vehicleType != null ||
            fuelType != null || expiringDocuments
}

class VehicleRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun page(
        page: Int,
        filters: VehicleFilters = VehicleFilters(),
        pageSize: Int = 20,
    ): ApiResult<PageDto<VehicleDto>> = safeCall(io) {
        api.vehicles(
            page = page,
            pageSize = pageSize,
            search = filters.search?.takeIf { it.isNotBlank() },
            status = filters.status,
            vehicleType = filters.vehicleType,
            fuelType = filters.fuelType,
            expiringDocuments = filters.expiringDocuments.takeIf { it },
        )
    }

    suspend fun byId(id: String): ApiResult<VehicleDto> = safeCall(io) { api.vehicle(id) }

    /** The vehicle currently assigned to the signed-in driver. */
    suspend fun mine(): ApiResult<VehicleDto> = safeCall(io) { api.myVehicle() }

    suspend fun create(body: VehicleCreateRequest): ApiResult<VehicleDto> =
        safeCall(io) { api.createVehicle(body) }

    suspend fun update(id: String, body: VehicleUpdateRequest): ApiResult<VehicleDto> =
        safeCall(io) { api.updateVehicle(id, body) }

    suspend fun qrPng(id: String): ApiResult<ByteArray> = withContext(io) {
        try {
            val response = api.vehicleQr(id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.bytes())
            } else {
                ApiResult.Failure(
                    ApiErrorParser.parse(response.code(), response.errorBody()?.string())
                )
            }
        } catch (io: IOException) {
            ApiResult.Failure(ApiError.network())
        }
    }

    suspend fun setActive(id: String, active: Boolean): ApiResult<VehicleDto> = safeCall(io) {
        if (active) api.activateVehicle(id) else api.deactivateVehicle(id)
    }
}
