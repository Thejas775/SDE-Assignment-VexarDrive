package com.thejas.fleetmanagementtask.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.data.repository.TripFilters
import com.thejas.fleetmanagementtask.data.repository.TripRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TripListUiState(
    val items: List<TripDto> = emptyList(),
    val filters: TripFilters = TripFilters(),
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

class TripListViewModel(private val repository: TripRepository) : ViewModel() {

    private val _state = MutableStateFlow(TripListUiState())
    val state: StateFlow<TripListUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun onStatusSelected(status: String?) {
        _state.value = _state.value.copy(filters = TripFilters(status = status))
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
                    _state.value = _state.value.copy(
                        items = if (mode == LoadMode.APPEND) _state.value.items + body.items
                        else body.items,
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

    fun messageShown() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private enum class LoadMode { FILTER, REFRESH, APPEND }

    companion object {
        val Factory = viewModelFactory {
            initializer { TripListViewModel(ServiceLocator.tripRepository) }
        }
    }
}
