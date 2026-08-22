package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentAssignRequest
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentDto
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentStatusRequest
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class IncidentFilters(
    val status: String? = null,
    val severity: String? = null,
    val openOnly: Boolean = false,
)

class IncidentRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun page(
        page: Int,
        filters: IncidentFilters = IncidentFilters(),
        pageSize: Int = 20,
    ): ApiResult<PageDto<IncidentDto>> = safeCall(io) {
        api.incidents(
            page = page,
            pageSize = pageSize,
            status = filters.status,
            severity = filters.severity,
            openOnly = filters.openOnly.takeIf { it },
        )
    }

    suspend fun mine(page: Int = 1, pageSize: Int = 50): ApiResult<PageDto<IncidentDto>> =
        safeCall(io) { api.myIncidents(page, pageSize) }

    suspend fun byId(id: String): ApiResult<IncidentDto> = safeCall(io) { api.incident(id) }

    suspend fun report(body: IncidentCreateRequest): ApiResult<IncidentDto> =
        safeCall(io) { api.reportIncident(body) }

    suspend fun assign(id: String, userId: String): ApiResult<IncidentDto> =
        safeCall(io) { api.assignIncident(id, IncidentAssignRequest(userId)) }

    suspend fun setStatus(
        id: String,
        status: String,
        resolutionNotes: String? = null,
    ): ApiResult<IncidentDto> =
        safeCall(io) { api.setIncidentStatus(id, IncidentStatusRequest(status, resolutionNotes)) }
}
