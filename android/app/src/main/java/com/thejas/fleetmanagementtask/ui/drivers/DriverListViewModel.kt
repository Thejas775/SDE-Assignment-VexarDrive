package com.thejas.fleetmanagementtask.ui.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.data.repository.DriverFilters
import com.thejas.fleetmanagementtask.data.repository.DriverRepository
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

data class DriverListUiState(
    val items: List<DriverDto> = emptyList(),
    val filters: DriverFilters = DriverFilters(),
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
class DriverListViewModel(private val repository: DriverRepository) : ViewModel() {

    private val _state = MutableStateFlow(DriverListUiState())
    val state: StateFlow<DriverListUiState> = _state.asStateFlow()

    private val searchQuery = MutableStateFlow("")
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            searchQuery.drop(1).debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()
                .collect { query -> applyFilters(_state.value.filters.copy(search = query)) }
        }
        refresh()
    }

    fun onSearchChanged(query: String) {
        searchQuery.value = query
    }

    fun onStatusSelected(status: String?) =
        applyFilters(_state.value.filters.copy(status = status))

    private fun applyFilters(filters: DriverFilters) {
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
                        items = if (mode == LoadMode.APPEND) {
                            _state.value.items + body.items
                        } else {
                            body.items
                        },
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
        const val SEARCH_DEBOUNCE_MS = 300L

        val Factory = viewModelFactory {
            initializer { DriverListViewModel(ServiceLocator.driverRepository) }
        }
    }
}
