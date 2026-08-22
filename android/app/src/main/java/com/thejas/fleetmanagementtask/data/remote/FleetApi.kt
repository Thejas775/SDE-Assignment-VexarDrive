package com.thejas.fleetmanagementtask.data.remote

import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentDto
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentEndRequest
import com.thejas.fleetmanagementtask.data.remote.dto.DashboardDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverPerformanceDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverUpdateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentAssignRequest
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentDto
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentStatusRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LocationBatchRequest
import com.thejas.fleetmanagementtask.data.remote.dto.LocationIngestResultDto
import com.thejas.fleetmanagementtask.data.remote.dto.LocationPointDto
import com.thejas.fleetmanagementtask.data.remote.dto.TripCancelRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripCompleteRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.data.remote.dto.TripStartRequest
import com.thejas.fleetmanagementtask.data.remote.dto.TripStatusRequest
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

    // ------------------------------------------------------------ drivers --

    @GET("drivers")
    suspend fun drivers(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("license_expiring") licenseExpiring: Boolean? = null,
    ): Response<PageDto<DriverDto>>

    @GET("drivers/{id}")
    suspend fun driver(@Path("id") id: String): Response<DriverDto>

    @POST("drivers")
    suspend fun createDriver(@Body body: DriverCreateRequest): Response<DriverDto>

    @PUT("drivers/{id}")
    suspend fun updateDriver(
        @Path("id") id: String,
        @Body body: DriverUpdateRequest,
    ): Response<DriverDto>

    @POST("drivers/{id}/activate")
    suspend fun activateDriver(@Path("id") id: String): Response<DriverDto>

    @POST("drivers/{id}/deactivate")
    suspend fun deactivateDriver(@Path("id") id: String): Response<DriverDto>

    @POST("drivers/{id}/suspend")
    suspend fun suspendDriver(@Path("id") id: String): Response<DriverDto>

    @GET("drivers/{id}/performance")
    suspend fun driverPerformance(@Path("id") id: String): Response<DriverPerformanceDto>

    // -------------------------------------------------------- assignments --

    @GET("assignments")
    suspend fun assignments(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
        @Query("driver_id") driverId: String? = null,
        @Query("vehicle_id") vehicleId: String? = null,
        @Query("status") status: String? = null,
        @Query("active_on") activeOn: String? = null,
    ): Response<PageDto<AssignmentDto>>

    @POST("assignments")
    suspend fun createAssignment(@Body body: AssignmentCreateRequest): Response<AssignmentDto>

    @POST("assignments/{id}/end")
    suspend fun endAssignment(
        @Path("id") id: String,
        @Body body: AssignmentEndRequest,
    ): Response<AssignmentDto>

    @POST("assignments/{id}/cancel")
    suspend fun cancelAssignment(@Path("id") id: String): Response<AssignmentDto>

    // -------------------------------------------------------------- trips --

    @GET("trips")
    suspend fun trips(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
        @Query("status") status: String? = null,
        @Query("vehicle_id") vehicleId: String? = null,
        @Query("driver_id") driverId: String? = null,
        @Query("active_only") activeOnly: Boolean? = null,
    ): Response<PageDto<TripDto>>

    @GET("trips/{id}")
    suspend fun trip(@Path("id") id: String): Response<TripDto>

    @POST("trips")
    suspend fun createTrip(@Body body: TripCreateRequest): Response<TripDto>

    @POST("trips/{id}/cancel")
    suspend fun cancelTrip(
        @Path("id") id: String,
        @Body body: TripCancelRequest,
    ): Response<TripDto>

    @GET("trips/{id}/route")
    suspend fun tripRoute(
        @Path("id") id: String,
        @Query("limit") limit: Int = 1000,
    ): Response<List<LocationPointDto>>

    // ------------------------------------------------------------- driver --

    @GET("vehicles/my-vehicle")
    suspend fun myVehicle(): Response<VehicleDto>

    @GET("trips/my")
    suspend fun myTrips(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
        @Query("status") status: String? = null,
        @Query("active_only") activeOnly: Boolean? = null,
    ): Response<PageDto<TripDto>>

    @POST("trips/{id}/start")
    suspend fun startTrip(
        @Path("id") id: String,
        @Body body: TripStartRequest,
    ): Response<TripDto>

    @POST("trips/{id}/status")
    suspend fun updateTripStatus(
        @Path("id") id: String,
        @Body body: TripStatusRequest,
    ): Response<TripDto>

    @POST("trips/{id}/complete")
    suspend fun completeTrip(
        @Path("id") id: String,
        @Body body: TripCompleteRequest,
    ): Response<TripDto>

    @POST("locations")
    suspend fun postLocations(
        @Body body: LocationBatchRequest,
    ): Response<LocationIngestResultDto>

    // ---------------------------------------------------------- incidents --

    @GET("incidents")
    suspend fun incidents(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
        @Query("status") status: String? = null,
        @Query("severity") severity: String? = null,
        @Query("vehicle_id") vehicleId: String? = null,
        @Query("open_only") openOnly: Boolean? = null,
    ): Response<PageDto<IncidentDto>>

    @GET("incidents/my")
    suspend fun myIncidents(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int = 20,
    ): Response<PageDto<IncidentDto>>

    @GET("incidents/{id}")
    suspend fun incident(@Path("id") id: String): Response<IncidentDto>

    @POST("incidents")
    suspend fun reportIncident(@Body body: IncidentCreateRequest): Response<IncidentDto>

    @POST("incidents/{id}/assign")
    suspend fun assignIncident(
        @Path("id") id: String,
        @Body body: IncidentAssignRequest,
    ): Response<IncidentDto>

    @POST("incidents/{id}/status")
    suspend fun setIncidentStatus(
        @Path("id") id: String,
        @Body body: IncidentStatusRequest,
    ): Response<IncidentDto>
}
