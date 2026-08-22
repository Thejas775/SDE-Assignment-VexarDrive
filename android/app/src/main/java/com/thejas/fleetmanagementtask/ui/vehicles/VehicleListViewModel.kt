package com.thejas.fleetmanagementtask.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.data.repository.VehicleFilters
import com.thejas.fleetmanagementtask.data.repository.VehicleRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class VehicleListUiState(
    val items: List<VehicleDto> = emptyList(),
    val filters: VehicleFilters = VehicleFilters(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val total: Int = 0,
    val page: Int = 0,
    val pages: Int = 0,
    val errorMessage: String? = null,
) {
    val hasMore: Boolean get() = page in 1..<pages
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
    val isBusy: Boolean get() = isLoading || isRefreshing || isLoadingMore
}

@OptIn(FlowPreview::class)
class VehicleListViewModel(private val repository: VehicleRepository) : ViewModel() {

    private val _state = MutableStateFlow(VehicleListUiState())
    val state: StateFlow<VehicleListUiState> = _state.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            searchQuery
                .drop(1) // the initial empty value is handled by the first load
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    applyFilters(_state.value.filters.copy(search = query))
                }
        }
        refresh()
    }

    fun onSearchChanged(query: String) {
        searchQuery.value = query
    }

    fun onStatusSelected(status: String?) {
        applyFilters(_state.value.filters.copy(status = status))
    }

    fun onExpiringOnlyChanged(enabled: Boolean) {
        applyFilters(_state.value.filters.copy(expiringDocuments = enabled))
    }

    fun clearFilters() = applyFilters(VehicleFilters())

    private fun applyFilters(filters: VehicleFilters) {
        _state.value = _state.value.copy(filters = filters)
        load(page = 1, mode = LoadMode.FILTER)
    }

    fun refresh() = load(page = 1, mode = LoadMode.REFRESH)

    fun loadNextPage() {
        val current = _state.value
        if (current.isBusy || !current.hasMore) return
        load(page = current.page + 1, mode = LoadMode.APPEND)
    }

    fun retry() = load(page = 1, mode = LoadMode.FILTER)

    private fun load(page: Int, mode: LoadMode) {
        // A newer request always wins: an in-flight page for stale filters must
        // not append its results after the filter changed.
        loadJob?.cancel()
        _state.value = _state.value.copy(
            isLoading = mode == LoadMode.FILTER,
            isRefreshing = mode == LoadMode.REFRESH,
            isLoadingMore = mode == LoadMode.APPEND,
            errorMessage = null,
        )
        loadJob = viewModelScope.launch {
            when (val result = repository.page(page, _state.value.filters)) {
                is ApiResult.Success -> {
                    val body = result.data
                    val merged = if (mode == LoadMode.APPEND) {
                        _state.value.items + body.items
                    } else {
                        body.items
                    }
                    _state.value = _state.value.copy(
                        items = merged,
                        total = body.total,
                        page = body.page,
                        pages = body.pages,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                    )
                }
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = result.error.message,
                )
            }
        }
    }

    /** Replaces one row after an edit without refetching the whole page. */
    fun replace(vehicle: VehicleDto) {
        _state.value = _state.value.copy(
            items = _state.value.items.map { if (it.id == vehicle.id) vehicle else it }
        )
    }

    fun messageShown() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private enum class LoadMode { FILTER, REFRESH, APPEND }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L

        val Factory = viewModelFactory {
            initializer { VehicleListViewModel(ServiceLocator.vehicleRepository) }
        }
    }
}
