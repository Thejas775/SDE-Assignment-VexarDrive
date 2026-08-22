package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.DashboardDto
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DashboardRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(): ApiResult<DashboardDto> = safeCall(io) { api.dashboard() }
}
