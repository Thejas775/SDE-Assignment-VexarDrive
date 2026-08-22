package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPointDto
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.dto.TripCancelRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripCompleteRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripStartRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripStatusRequest
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

    // ------------------------------------------------------------- driver --

    suspend fun myTrips(
        page: Int,
        filters: TripFilters = TripFilters(),
        pageSize: Int = 20,
    ): ApiResult<PageDto<TripDto>> = safeCall(io) {
        api.myTrips(
            page = page,
            pageSize = pageSize,
            status = filters.status,
            activeOnly = filters.activeOnly.takeIf { it },
        )
    }

    suspend fun start(
        id: String,
        odometer: Int,
        latitude: String? = null,
        longitude: String? = null,
    ): ApiResult<TripDto> = safeCall(io) {
        api.startTrip(id, TripStartRequest(odometer, latitude, longitude))
    }

    suspend fun setStatus(id: String, status: String): ApiResult<TripDto> =
        safeCall(io) { api.updateTripStatus(id, TripStatusRequest(status)) }

    suspend fun complete(
        id: String,
        odometer: Int,
        latitude: String? = null,
        longitude: String? = null,
    ): ApiResult<TripDto> = safeCall(io) {
        api.completeTrip(id, TripCompleteRequest(odometer, latitude, longitude))
    }
}
