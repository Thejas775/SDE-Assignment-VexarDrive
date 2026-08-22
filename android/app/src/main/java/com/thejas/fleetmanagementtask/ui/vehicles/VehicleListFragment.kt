package com.thejas.fleetmanagementtask.ui.vehicles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.databinding.FragmentVehiclesBinding
import kotlinx.coroutines.launch

class VehicleListFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehicleListViewModel by viewModels { VehicleListViewModel.Factory }
    private lateinit var adapter: VehicleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = VehicleAdapter { vehicle ->
            VehicleDetailSheet.newInstance(vehicle.id)
                .show(childFragmentManager, VehicleDetailSheet.TAG)
        }
        binding.vehicleList.layoutManager = LinearLayoutManager(requireContext())
        binding.vehicleList.adapter = adapter
        binding.vehicleList.addOnScrollListener(PagingScrollListener { viewModel.loadNextPage() })

        binding.searchInput.doAfterTextChanged {
            viewModel.onSearchChanged(it?.toString().orEmpty())
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.addVehicleFab.setOnClickListener {
            VehicleFormSheet.newInstance(null).show(childFragmentManager, VehicleFormSheet.TAG)
        }

        buildStatusChips()
        listenForChanges()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun buildStatusChips() {
        val options = listOf<String?>(null) + FleetEnums.vehicleStatuses
        options.forEach { status ->
            val chip = Chip(requireContext()).apply {
                text = status?.let(FleetEnums::label) ?: getString(R.string.filter_all)
                isCheckable = true
                isChecked = status == null
                tag = status
            }
            binding.statusChips.addView(chip)
        }
        binding.statusChips.setOnCheckedStateChangeListener { group, ids ->
            val selected = ids.firstOrNull()?.let { group.findViewById<Chip>(it)?.tag as? String }
            viewModel.onStatusSelected(selected)
        }
    }

    /** Detail and form sheets report their result through the fragment result API. */
    private fun listenForChanges() {
        childFragmentManager.setFragmentResultListener(RESULT_KEY, viewLifecycleOwner) { _, _ ->
            viewModel.refresh()
        }
    }

    private fun render(state: VehicleListUiState) {
        adapter.submitList(state.items)
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.loadingIndicator.visibility =
            if (state.isLoading) View.VISIBLE else View.GONE
        binding.emptyText.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        binding.resultCount.text = getString(R.string.vehicles_count, state.total)

        state.errorMessage?.let { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { viewModel.retry() }
                .show()
            viewModel.messageShown()
        }
    }

    override fun onDestroyView() {
        binding.vehicleList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    /** Requests the next page once the last few rows come into view. */
    private class PagingScrollListener(
        private val onLoadMore: () -> Unit,
    ) : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (lastVisible >= layoutManager.itemCount - PREFETCH_DISTANCE) onLoadMore()
        }

        private companion object {
            const val PREFETCH_DISTANCE = 4
        }
    }

    companion object {
        const val RESULT_KEY = "vehicle_changed"
    }
}
