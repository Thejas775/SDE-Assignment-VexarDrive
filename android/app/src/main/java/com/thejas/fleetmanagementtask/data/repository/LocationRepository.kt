package com.thejas.fleetmanagementtask.data.repository

import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.remote.dto.LocationBatchRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LocationIngestResultDto
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPingRequest
import com.thejas.fleetmanagementtask.data.remote.safeCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class LocationRepository(
    private val api: FleetApi,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun send(
        tripId: String,
        pings: List<LocationPingRequest>,
    ): ApiResult<LocationIngestResultDto> =
        safeCall(io) { api.postLocations(LocationBatchRequest(tripId, pings)) }
}
