package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.data.remote.dto.DashboardDto
import retrofit2.Response
import retrofit2.http.GET

/** Fleet-manager endpoints. Grows as screens land. */
interface FleetApi {

    @GET("dashboard")
    suspend fun dashboard(): Response<DashboardDto>
}
