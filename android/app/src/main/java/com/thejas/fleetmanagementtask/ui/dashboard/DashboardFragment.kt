package com.thejas.fleetmanagementtask.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory }
    private val statAdapter = StatAdapter()
    private val incidentAdapter = RecentIncidentAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.greetingText.setText(R.string.dashboard_title)
        binding.statsGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.statsGrid.adapter = statAdapter
        binding.incidentsList.layoutManager = LinearLayoutManager(requireContext())
        binding.incidentsList.adapter = incidentAdapter
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: DashboardUiState) {
        binding.swipeRefresh.isRefreshing = state.isRefreshing

        state.data?.let { data ->
            statAdapter.submitList(data.toStats())
            incidentAdapter.submitList(data.recentIncidents)
        }
        binding.incidentsEmpty.visibility =
            if (state.showEmptyIncidents) View.VISIBLE else View.GONE
        binding.incidentsList.visibility =
            if (state.showEmptyIncidents) View.GONE else View.VISIBLE

        state.errorMessage?.let { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { viewModel.refresh() }
                .show()
            viewModel.messageShown()
        }
    }

    override fun onDestroyView() {
        // Adapters hold the binding's views; clearing prevents a leak when the
        // fragment outlives its view.
        binding.statsGrid.adapter = null
        binding.incidentsList.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
