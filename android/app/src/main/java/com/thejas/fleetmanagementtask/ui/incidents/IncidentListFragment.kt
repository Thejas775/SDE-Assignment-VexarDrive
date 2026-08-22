package com.thejas.fleetmanagementtask.ui.incidents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.databinding.FragmentIncidentsBinding
import kotlinx.coroutines.launch

class IncidentListFragment : Fragment() {

    private var _binding: FragmentIncidentsBinding? = null
    private val binding get() = _binding!!

    private val mineOnly: Boolean get() = arguments?.getBoolean(ARG_MINE) ?: false
    private val viewModel: IncidentListViewModel by viewModels {
        IncidentListViewModel.factory(mineOnly)
    }
    private lateinit var adapter: IncidentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentIncidentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = IncidentAdapter { incident ->
            IncidentDetailSheet.newInstance(incident.id)
                .show(childFragmentManager, IncidentDetailSheet.TAG)
        }
        binding.incidentList.layoutManager = LinearLayoutManager(requireContext())
        binding.incidentList.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        // Reporting starts from the vehicle a driver is actually holding.
        binding.reportIncidentFab.visibility = View.GONE

        if (mineOnly) {
            binding.statusChips.visibility = View.GONE
        } else {
            val options = listOf<String?>(null) + FleetEnums.incidentStatuses
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
        }

        childFragmentManager.setFragmentResultListener(RESULT_KEY, viewLifecycleOwner) { _, _ ->
            viewModel.refresh()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.items)
                    binding.swipeRefresh.isRefreshing = state.isRefreshing
                    binding.loadingIndicator.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.emptyText.visibility = if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.resultCount.text = getString(R.string.incidents_count, state.total)
                    state.errorMessage?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                        viewModel.messageShown()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.incidentList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val RESULT_KEY = "incident_changed"
        private const val ARG_MINE = "mine_only"

        fun newInstance(mineOnly: Boolean) = IncidentListFragment().apply {
            arguments = bundleOf(ARG_MINE to mineOnly)
        }
    }
}
