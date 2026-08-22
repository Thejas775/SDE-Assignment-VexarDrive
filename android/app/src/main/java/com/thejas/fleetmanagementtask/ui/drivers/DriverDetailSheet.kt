package com.thejas.fleetmanagementtask.ui.drivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.databinding.SheetDriverDetailBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.dashboard.Stat
import com.thejas.fleetmanagementtask.ui.dashboard.StatAdapter
import kotlinx.coroutines.launch

class DriverDetailSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDriverDetailBinding? = null
    private val binding get() = _binding!!
    private val drivers get() = ServiceLocator.driverRepository
    private val assignments get() = ServiceLocator.assignmentRepository
    private val driverId: String get() = requireArguments().getString(ARG_ID)!!

    private var driver: DriverDto? = null
    private val statAdapter = StatAdapter()
    private lateinit var assignmentAdapter: AssignmentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetDriverDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.performanceGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.performanceGrid.adapter = statAdapter
        assignmentAdapter = AssignmentAdapter(::confirmEnd)
        binding.assignmentList.layoutManager = LinearLayoutManager(requireContext())
        binding.assignmentList.adapter = assignmentAdapter

        binding.editButton.setOnClickListener {
            DriverFormSheet.newInstance(driverId).show(parentFragmentManager, DriverFormSheet.TAG)
            dismiss()
        }
        binding.assignButton.setOnClickListener {
            AssignmentFormSheet.newInstance(driverId, driver?.fullName.orEmpty())
                .show(childFragmentManager, AssignmentFormSheet.TAG)
        }
        binding.statusButton.setOnClickListener { toggleStatus() }

        childFragmentManager.setFragmentResultListener(RESULT_KEY, viewLifecycleOwner) { _, _ ->
            loadAssignments()
            loadDriver()
            notifyChanged()
        }

        loadDriver()
        loadPerformance()
        loadAssignments()
    }

    private fun loadDriver() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = drivers.byId(driverId)) {
            is ApiResult.Success -> bind(result.data)
            is ApiResult.Failure -> {
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    private fun loadPerformance() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = drivers.performance(driverId)) {
            is ApiResult.Success -> statAdapter.submitList(
                listOf(
                    Stat(R.string.perf_trips, result.data.totalTrips.toString()),
                    Stat(R.string.perf_completed, result.data.completedTrips.toString()),
                    Stat(R.string.perf_distance, result.data.totalDistanceKm.toString()),
                    Stat(
                        R.string.perf_avg_duration,
                        result.data.averageTripDurationMinutes?.toString() ?: "-",
                    ),
                    Stat(R.string.perf_incidents, result.data.incidentsReported.toString()),
                )
            )
            is ApiResult.Failure -> Unit
        }
    }

    private fun loadAssignments() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = assignments.forDriver(driverId)) {
            is ApiResult.Success -> {
                assignmentAdapter.submitList(result.data.items)
                binding.assignmentsEmpty.visibility =
                    if (result.data.items.isEmpty()) View.VISIBLE else View.GONE
            }
            is ApiResult.Failure -> Unit
        }
    }

    private fun bind(data: DriverDto) {
        driver = data
        binding.nameText.text = data.fullName
        binding.contactText.text = listOfNotNull(data.email, data.phoneNumber).joinToString(" · ")
        binding.licenceText.text =
            "${getString(R.string.driver_licence, data.licenseNumber)} · expires ${data.licenseExpiry}"
        binding.licenceWarning.visibility = if (data.needsAttention) View.VISIBLE else View.GONE
        binding.statusChip.text = FleetEnums.label(data.status)
        binding.statusButton.setText(
            if (data.status == "ACTIVE") R.string.driver_suspend else R.string.driver_activate
        )
    }

    private fun toggleStatus() {
        val current = driver ?: return
        val next = if (current.status == "ACTIVE") "SUSPENDED" else "ACTIVE"
        binding.statusButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = drivers.setStatus(driverId, next)) {
                is ApiResult.Success -> {
                    bind(result.data)
                    notifyChanged()
                }
                // A driver with open trips cannot be suspended; the server explains.
                is ApiResult.Failure ->
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
            }
            binding.statusButton.isEnabled = true
        }
    }

    private fun confirmEnd(assignment: AssignmentDto) {
        if (assignment.status != "ACTIVE") return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.assignment_end_now)
            .setMessage(assignment.vehicle.label)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.assignment_end_now) { _, _ -> endAssignment(assignment) }
            .show()
    }

    private fun endAssignment(assignment: AssignmentDto) =
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = assignments.end(assignment.id)) {
                is ApiResult.Success -> {
                    loadAssignments()
                    loadDriver()
                    notifyChanged()
                }
                is ApiResult.Failure ->
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
            }
        }

    private fun notifyChanged() =
        setFragmentResult(DriverListFragment.RESULT_KEY, bundleOf())

    override fun onDestroyView() {
        binding.performanceGrid.adapter = null
        binding.assignmentList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "DriverDetailSheet"
        const val RESULT_KEY = "assignment_changed"
        private const val ARG_ID = "driver_id"

        fun newInstance(id: String) = DriverDetailSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
