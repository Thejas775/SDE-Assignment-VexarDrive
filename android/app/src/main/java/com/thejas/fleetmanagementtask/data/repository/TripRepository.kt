package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPointDto
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.dto.TripCancelRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class TripFilters(
    val status: String? = null,
    val activeOnly: Boolean = false,
    val driverId: String? = null,
)

class TripRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun page(
        page: Int,
        filters: TripFilters = TripFilters(),
        pageSize: Int = 20,
    ): ApiResult<PageDto<TripDto>> = safeCall(io) {
        api.trips(
            page = page,
            pageSize = pageSize,
            status = filters.status,
            driverId = filters.driverId,
            activeOnly = filters.activeOnly.takeIf { it },
        )
    }

    suspend fun byId(id: String): ApiResult<TripDto> = safeCall(io) { api.trip(id) }

    suspend fun create(body: TripCreateRequest): ApiResult<TripDto> =
        safeCall(io) { api.createTrip(body) }

    suspend fun cancel(id: String, reason: String?): ApiResult<TripDto> =
        safeCall(io) { api.cancelTrip(id, TripCancelRequest(reason)) }

    suspend fun route(id: String): ApiResult<List<LocationPointDto>> =
        safeCall(io) { api.tripRoute(id) }
}
