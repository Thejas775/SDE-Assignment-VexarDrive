package com.thejas.fleetmanagementtask.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.DashboardDto
import com.thejas.fleetmanagementtask.data.repository.DashboardRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val data: DashboardDto? = null,
    val errorMessage: String? = null,
) {
    val showEmptyIncidents: Boolean get() = data?.recentIncidents?.isEmpty() == true
}

class DashboardViewModel(private val repository: DashboardRepository) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        val current = _state.value
        if (current.isLoading || current.isRefreshing) return

        _state.value = current.copy(
            // Only blank the screen on a first load; a pull-to-refresh keeps
            // the previous numbers visible while the new ones arrive.
            isLoading = !isRefresh && current.data == null,
            isRefreshing = isRefresh,
            errorMessage = null,
        )
        viewModelScope.launch {
            _state.value = when (val result = repository.load()) {
                is ApiResult.Success -> _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    data = result.data,
                )
                is ApiResult.Failure -> _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    fun messageShown() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { DashboardViewModel(ServiceLocator.dashboardRepository) }
        }
    }
}
