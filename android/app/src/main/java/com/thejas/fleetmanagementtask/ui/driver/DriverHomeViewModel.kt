package com.thejas.fleetmanagementtask.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.data.repository.TripRepository
import com.thejas.fleetmanagementtask.data.repository.VehicleRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DriverHomeUiState(
    val vehicle: VehicleDto? = null,
    val trip: TripDto? = null,
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val vehicleMessage: String? = null,
) {
    val hasTrip: Boolean get() = trip != null
    val canStart: Boolean get() = trip?.status == "SCHEDULED"
    val canComplete: Boolean get() = trip?.isActive == true
}

sealed interface DriverHomeEvent {
    data class Started(val trip: TripDto) : DriverHomeEvent
    data class Completed(val trip: TripDto) : DriverHomeEvent
    data class Message(val text: String) : DriverHomeEvent
}

class DriverHomeViewModel(
    private val vehicles: VehicleRepository,
    private val trips: TripRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverHomeUiState())
    val state: StateFlow<DriverHomeUiState> = _state.asStateFlow()

    private val _events = Channel<DriverHomeEvent>(Channel.BUFFERED)
    val events: Flow<DriverHomeEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            loadVehicle()
            loadTrip()
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    private suspend fun loadVehicle() {
        _state.value = when (val result = vehicles.mine()) {
            is ApiResult.Success -> _state.value.copy(vehicle = result.data, vehicleMessage = null)
            // 404 here simply means nothing is assigned yet; that is not an error.
            is ApiResult.Failure -> _state.value.copy(
                vehicle = null,
                vehicleMessage = result.error.message,
            )
        }
    }

    /** The running trip if there is one, otherwise the next scheduled one. */
    private suspend fun loadTrip() {
        val active = trips.myTrips(
            page = 1,
            filters = com.thejas.fleetmanagementtask.data.repository.TripFilters(activeOnly = true),
            pageSize = 1,
        )
        val running = (active as? ApiResult.Success)?.data?.items?.firstOrNull()
        if (running != null) {
            _state.value = _state.value.copy(trip = running)
            return
        }
        val scheduled = trips.myTrips(
            page = 1,
            filters = com.thejas.fleetmanagementtask.data.repository.TripFilters(status = "SCHEDULED"),
            pageSize = 1,
        )
        _state.value = _state.value.copy(
            trip = (scheduled as? ApiResult.Success)?.data?.items?.firstOrNull()
        )
    }

    fun start(odometer: Int, latitude: String?, longitude: String?) {
        val trip = _state.value.trip ?: return
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true)
        viewModelScope.launch {
            when (val result = trips.start(trip.id, odometer, latitude, longitude)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(trip = result.data, isWorking = false)
                    _events.send(DriverHomeEvent.Started(result.data))
                    loadVehicle()
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(isWorking = false)
                    _events.send(DriverHomeEvent.Message(result.error.message))
                }
            }
        }
    }

    fun complete(odometer: Int, latitude: String?, longitude: String?) {
        val trip = _state.value.trip ?: return
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true)
        viewModelScope.launch {
            when (val result = trips.complete(trip.id, odometer, latitude, longitude)) {
                is ApiResult.Success -> {
                    _events.send(DriverHomeEvent.Completed(result.data))
                    _state.value = _state.value.copy(isWorking = false)
                    // The finished trip is replaced by whatever comes next.
                    refresh()
                }
                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(isWorking = false)
                    _events.send(DriverHomeEvent.Message(result.error.message))
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DriverHomeViewModel(
                    ServiceLocator.vehicleRepository,
                    ServiceLocator.tripRepository,
                )
            }
        }
    }
}
