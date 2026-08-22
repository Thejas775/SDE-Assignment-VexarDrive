package com.thejas.fleetmanagementtask.di

import android.content.Context
import com.thejas.fleetmanagementtask.data.local.EncryptedTokenStore
import com.thejas.fleetmanagementtask.data.local.TokenStore
import com.thejas.fleetmanagementtask.data.remote.ApiFactory
import com.thejas.fleetmanagementtask.data.remote.AuthApi
import com.thejas.fleetmanagementtask.data.remote.FleetApi
import com.thejas.fleetmanagementtask.data.repository.AssignmentRepository
import com.thejas.fleetmanagementtask.data.repository.AuthRepository
import com.thejas.fleetmanagementtask.data.repository.DashboardRepository
import com.thejas.fleetmanagementtask.data.repository.DriverRepository
import com.thejas.fleetmanagementtask.data.repository.LocationRepository
import com.thejas.fleetmanagementtask.data.repository.TripRepository
import com.thejas.fleetmanagementtask.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import retrofit2.Retrofit

/**
 * Hand-rolled dependency container.
 *
 * Hilt would be the usual choice, but this app has a single graph of about a
 * dozen objects with no scoping beyond "one per process". A visible, ordered
 * container is easier to follow than annotations plus generated code, and it
 * keeps the build free of an annotation processor.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when a refresh token is rejected, so the UI can return to login. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    val tokenStore: TokenStore by lazy { EncryptedTokenStore(appContext) }

    private val retrofit: Retrofit by lazy {
        ApiFactory.create(tokenStore) { _sessionExpired.tryEmit(Unit) }
    }

    private val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    private val fleetApi: FleetApi by lazy { retrofit.create(FleetApi::class.java) }

    val authRepository: AuthRepository by lazy { AuthRepository(authApi, tokenStore) }
    val dashboardRepository: DashboardRepository by lazy { DashboardRepository(fleetApi) }
    val vehicleRepository: VehicleRepository by lazy { VehicleRepository(fleetApi) }
    val driverRepository: DriverRepository by lazy { DriverRepository(fleetApi) }
    val assignmentRepository: AssignmentRepository by lazy { AssignmentRepository(fleetApi) }
    val tripRepository: TripRepository by lazy { TripRepository(fleetApi) }
    val locationRepository: LocationRepository by lazy { LocationRepository(fleetApi) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
