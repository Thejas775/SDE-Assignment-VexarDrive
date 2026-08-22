package com.thejas.fleetmanagementtask.ui.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentDto
import com.thejas.fleetmanagementtask.data.repository.IncidentFilters
import com.thejas.fleetmanagementtask.data.repository.IncidentRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class IncidentListUiState(
    val items: List<IncidentDto> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}

/** [mineOnly] switches between the manager's queue and a driver's own reports. */
class IncidentListViewModel(
    private val repository: IncidentRepository,
    private val mineOnly: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(IncidentListUiState())
    val state: StateFlow<IncidentListUiState> = _state.asStateFlow()

    private var status: String? = null
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onStatusSelected(value: String?) {
        status = value
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        loadJob?.cancel()
        _state.value = _state.value.copy(
            isLoading = !isRefresh,
            isRefreshing = isRefresh,
            errorMessage = null,
        )
        loadJob = viewModelScope.launch {
            val result = if (mineOnly) {
                repository.mine()
            } else {
                repository.page(1, IncidentFilters(status = status), pageSize = 50)
            }
            _state.value = when (result) {
                is ApiResult.Success -> IncidentListUiState(
                    items = result.data.items,
                    total = result.data.total,
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
        fun factory(mineOnly: Boolean) = viewModelFactory {
            initializer { IncidentListViewModel(ServiceLocator.incidentRepository, mineOnly) }
        }
    }
}
