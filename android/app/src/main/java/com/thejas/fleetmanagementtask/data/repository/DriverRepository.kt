package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.DriverCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverPerformanceDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverUpdateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class DriverFilters(
    val search: String? = null,
    val status: String? = null,
    val licenseExpiring: Boolean = false,
)

class DriverRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun page(
        page: Int,
        filters: DriverFilters = DriverFilters(),
        pageSize: Int = 20,
    ): ApiResult<PageDto<DriverDto>> = safeCall(io) {
        api.drivers(
            page = page,
            pageSize = pageSize,
            search = filters.search?.takeIf { it.isNotBlank() },
            status = filters.status,
            licenseExpiring = filters.licenseExpiring.takeIf { it },
        )
    }

    suspend fun byId(id: String): ApiResult<DriverDto> = safeCall(io) { api.driver(id) }

    suspend fun create(body: DriverCreateRequest): ApiResult<DriverDto> =
        safeCall(io) { api.createDriver(body) }

    suspend fun update(id: String, body: DriverUpdateRequest): ApiResult<DriverDto> =
        safeCall(io) { api.updateDriver(id, body) }

    suspend fun setStatus(id: String, status: String): ApiResult<DriverDto> = safeCall(io) {
        when (status) {
            "ACTIVE" -> api.activateDriver(id)
            "SUSPENDED" -> api.suspendDriver(id)
            else -> api.deactivateDriver(id)
        }
    }

    suspend fun performance(id: String): ApiResult<DriverPerformanceDto> =
        safeCall(io) { api.driverPerformance(id) }
}
