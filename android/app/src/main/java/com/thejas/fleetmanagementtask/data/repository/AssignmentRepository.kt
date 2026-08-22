package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentDto
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentEndRequest
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AssignmentRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun forDriver(driverId: String, page: Int = 1): ApiResult<PageDto<AssignmentDto>> =
        safeCall(io) { api.assignments(page = page, driverId = driverId) }

    suspend fun create(body: AssignmentCreateRequest): ApiResult<AssignmentDto> =
        safeCall(io) { api.createAssignment(body) }

    suspend fun end(id: String, endDate: String? = null): ApiResult<AssignmentDto> =
        safeCall(io) { api.endAssignment(id, AssignmentEndRequest(endDate)) }

    suspend fun cancel(id: String): ApiResult<AssignmentDto> =
        safeCall(io) { api.cancelAssignment(id) }
}
