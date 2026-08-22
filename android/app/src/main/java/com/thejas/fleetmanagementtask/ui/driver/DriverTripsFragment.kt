package com.thejas.fleetmanagementtask.ui.driver

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.data.repository.TripFilters
import com.thejas.fleetmanagementtask.data.repository.TripRepository
import com.thejas.fleetmanagementtask.databinding.FragmentTripsBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.trips.TripAdapter
import com.thejas.fleetmanagementtask.ui.trips.TripDetailSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The driver's own trips; the same list the manager sees, scoped by the API. */
class MyTripsViewModel(private val repository: TripRepository) : ViewModel() {

    data class UiState(
        val items: List<TripDto> = emptyList(),
        val total: Int = 0,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var status: String? = null

    init {
        refresh()
    }

    fun onStatusSelected(value: String?) {
        status = value
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            _state.value = when (
                val result = repository.myTrips(1, TripFilters(status = status), pageSize = 50)
            ) {
                is ApiResult.Success -> UiState(result.data.items, result.data.total)
                is ApiResult.Failure -> _state.value.copy(
                    isLoading = false,
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
            initializer { MyTripsViewModel(ServiceLocator.tripRepository) }
        }
    }
}

class DriverTripsFragment : Fragment() {

    private var _binding: FragmentTripsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MyTripsViewModel by viewModels { MyTripsViewModel.Factory }
    private lateinit var adapter: TripAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTripsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TripAdapter { trip ->
            TripDetailSheet.newInstance(trip.id).show(childFragmentManager, TripDetailSheet.TAG)
        }
        binding.tripList.layoutManager = LinearLayoutManager(requireContext())
        binding.tripList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        // Creating trips is a manager action.
        binding.addTripFab.visibility = View.GONE

        val options = listOf<String?>(null) + FleetEnums.tripStatuses
        options.forEach { status ->
            binding.statusChips.addView(
                Chip(requireContext()).apply {
                    text = status?.let(FleetEnums::label) ?: getString(R.string.filter_all)
                    isCheckable = true
                    isChecked = status == null
                    tag = status
                }
            )
        }
        binding.statusChips.setOnCheckedStateChangeListener { group, ids ->
            viewModel.onStatusSelected(
                ids.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? String }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.items)
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    binding.emptyText.visibility =
                        if (!state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
                    binding.resultCount.text = getString(R.string.trips_count, state.total)
                    state.errorMessage?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.messageShown()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.tripList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
