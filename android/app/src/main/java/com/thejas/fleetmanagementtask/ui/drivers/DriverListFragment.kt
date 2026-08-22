package com.thejas.fleetmanagementtask.ui.drivers

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
import com.thejas.fleetmanagementtask.databinding.FragmentDriversBinding
import kotlinx.coroutines.launch

class DriverListFragment : Fragment() {

    private var _binding: FragmentDriversBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DriverListViewModel by viewModels { DriverListViewModel.Factory }
    private lateinit var adapter: DriverAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDriversBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DriverAdapter { driver ->
            DriverDetailSheet.newInstance(driver.id).show(childFragmentManager, DriverDetailSheet.TAG)
        }
        binding.driverList.layoutManager = LinearLayoutManager(requireContext())
        binding.driverList.adapter = adapter
        binding.driverList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= manager.itemCount - 4) {
                    viewModel.loadNextPage()
                }
            }
        })

        binding.searchInput.doAfterTextChanged {
            viewModel.onSearchChanged(it?.toString().orEmpty())
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.addDriverFab.setOnClickListener {
            DriverFormSheet.newInstance(null).show(childFragmentManager, DriverFormSheet.TAG)
        }

        val options = listOf<String?>(null) + FleetEnums.driverStatuses
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

        childFragmentManager.setFragmentResultListener(RESULT_KEY, viewLifecycleOwner) { _, _ ->
            viewModel.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: DriverListUiState) {
        adapter.submitList(state.items)
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.emptyText.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
        binding.resultCount.text = getString(R.string.drivers_count, state.total)

        state.errorMessage?.let { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { viewModel.retry() }
                .show()
            viewModel.messageShown()
        }
    }

    override fun onDestroyView() {
        binding.driverList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val RESULT_KEY = "driver_changed"
    }
}
