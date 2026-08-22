package com.thejas.fleetmanagementtask.ui.drivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.AssignmentCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.data.repository.VehicleFilters
import com.thejas.fleetmanagementtask.databinding.SheetAssignmentFormBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.common.formatUtcDate
import kotlinx.coroutines.launch

class AssignmentFormSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAssignmentFormBinding? = null
    private val binding get() = _binding!!
    private val driverId: String get() = requireArguments().getString(ARG_DRIVER_ID)!!
    private var vehicles: List<VehicleDto> = emptyList()
    private var selectedVehicleId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetAssignmentFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.driverText.text = requireArguments().getString(ARG_DRIVER_NAME)

        binding.startInput.setOnClickListener { pickDate(R.string.assignment_start) { binding.startInput.setText(it) } }
        binding.endInput.setOnClickListener { pickDate(R.string.assignment_end) { binding.endInput.setText(it) } }
        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { dismiss() }

        loadVehicles()
    }

    private fun pickDate(titleRes: Int, onPicked: (String) -> Unit) {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(titleRes))
            .build()
            .apply { addOnPositiveButtonClickListener { onPicked(formatUtcDate(it)) } }
            .show(childFragmentManager, "date")
    }

    /** Only assignable vehicles are offered; inactive ones are rejected by the API. */
    private fun loadVehicles() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = ServiceLocator.vehicleRepository.page(
            page = 1,
            filters = VehicleFilters(status = "AVAILABLE"),
            pageSize = 100,
        )) {
            is ApiResult.Success -> {
                vehicles = result.data.items
                binding.vehicleInput.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        vehicles.map { "${it.registrationNumber} · ${it.make} ${it.model}" },
                    )
                )
                binding.vehicleInput.setOnItemClickListener { _, _, position, _ ->
                    selectedVehicleId = vehicles[position].id
                    binding.vehicleLayout.error = null
                }
            }
            is ApiResult.Failure ->
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun save() {
        binding.conflictCard.visibility = View.GONE
        val vehicleId = selectedVehicleId
        val start = binding.startInput.text.toString()
        var ok = true
        if (vehicleId == null) {
            binding.vehicleLayout.error = getString(R.string.error_required)
            ok = false
        }
        if (start.isBlank()) {
            binding.startLayout.error = getString(R.string.error_required)
            ok = false
        }
        if (!ok) return

        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ServiceLocator.assignmentRepository.create(
                AssignmentCreateRequest(
                    vehicleId = vehicleId!!,
                    driverId = driverId,
                    startDate = start,
                    endDate = binding.endInput.text.toString().takeIf { it.isNotBlank() },
                    notes = binding.notesInput.text.toString().takeIf { it.isNotBlank() },
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    setFragmentResult(DriverDetailSheet.RESULT_KEY, bundleOf())
                    dismiss()
                }
                is ApiResult.Failure -> {
                    // Section 8: an overlapping period comes back as a 409 naming
                    // the assignment it clashes with. That belongs on screen, not
                    // in a transient snackbar.
                    if (result.error.isConflict) {
                        binding.conflictText.text = result.error.message
                        binding.conflictCard.visibility = View.VISIBLE
                    } else {
                        binding.startLayout.error = result.error.fieldErrors["start_date"]
                        binding.endLayout.error = result.error.fieldErrors["end_date"]
                        if (result.error.fieldErrors.isEmpty()) {
                            Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG)
                                .show()
                        }
                    }
                    setEnabled(true)
                }
            }
        }
    }

    private fun setEnabled(enabled: Boolean) {
        binding.saveButton.isEnabled = enabled
        binding.formProgress.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "AssignmentFormSheet"
        private const val ARG_DRIVER_ID = "driver_id"
        private const val ARG_DRIVER_NAME = "driver_name"

        fun newInstance(driverId: String, driverName: String) = AssignmentFormSheet().apply {
            arguments = bundleOf(ARG_DRIVER_ID to driverId, ARG_DRIVER_NAME to driverName)
        }
    }
}
