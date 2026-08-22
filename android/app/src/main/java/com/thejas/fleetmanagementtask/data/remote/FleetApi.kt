package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.data.remote.dto.DashboardDto
import com.thejas.fleetmanagementtask.data.remote.dto.PageDto
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleUpdateRequest
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Fleet-manager endpoints. Grows as screens land. */
interface FleetApi {

    @GET("dashboard")
    suspend fun dashboard(): Response<DashboardDto>

    @GET("vehicles")
    suspend fun vehicles(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("vehicle_type") vehicleType: String? = null,
        @Query("fuel_type") fuelType: String? = null,
        @Query("expiring_documents") expiringDocuments: Boolean? = null,
    ): Response<PageDto<VehicleDto>>

    @GET("vehicles/{id}")
    suspend fun vehicle(@Path("id") id: String): Response<VehicleDto>

    @POST("vehicles")
    suspend fun createVehicle(@Body body: VehicleCreateRequest): Response<VehicleDto>

    @PUT("vehicles/{id}")
    suspend fun updateVehicle(
        @Path("id") id: String,
        @Body body: VehicleUpdateRequest,
    ): Response<VehicleDto>

    @Streaming
    @GET("vehicles/{id}/qr")
    suspend fun vehicleQr(@Path("id") id: String): Response<ResponseBody>

    @POST("vehicles/{id}/activate")
    suspend fun activateVehicle(@Path("id") id: String): Response<VehicleDto>

    @POST("vehicles/{id}/deactivate")
    suspend fun deactivateVehicle(@Path("id") id: String): Response<VehicleDto>
}
