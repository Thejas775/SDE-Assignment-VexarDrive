package com.thejas.fleetmanagementtask.ui.incidents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentCreateRequest
import com.thejas.fleetmanagementtask.databinding.SheetIncidentFormBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.launch

class IncidentFormSheet : BottomSheetDialogFragment() {

    private var _binding: SheetIncidentFormBinding? = null
    private val binding get() = _binding!!

    private val vehicleId: String get() = requireArguments().getString(ARG_VEHICLE_ID)!!
    private val tripId: String? get() = arguments?.getString(ARG_TRIP_ID)
    private var severity: String = DEFAULT_SEVERITY

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetIncidentFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vehicleText.text = requireArguments().getString(ARG_VEHICLE_LABEL)

        FleetEnums.incidentSeverities.forEach { value ->
            binding.severityChips.addView(
                Chip(requireContext()).apply {
                    text = FleetEnums.label(value)
                    isCheckable = true
                    isChecked = value == DEFAULT_SEVERITY
                    tag = value
                }
            )
        }
        binding.severityChips.setOnCheckedStateChangeListener { group, ids ->
            severity = ids.firstOrNull()
                ?.let { group.findViewById<Chip>(it)?.tag as? String }
                ?: DEFAULT_SEVERITY
        }

        binding.titleInput.doAfterTextChanged { binding.titleLayout.error = null }
        binding.descriptionInput.doAfterTextChanged { binding.descriptionLayout.error = null }
        binding.saveButton.setOnClickListener { submit() }
        binding.cancelButton.setOnClickListener { dismiss() }
    }

    private fun submit() {
        val title = binding.titleInput.text.toString().trim()
        val description = binding.descriptionInput.text.toString().trim()
        var ok = true
        if (title.length < MIN_LENGTH) {
            binding.titleLayout.error = getString(R.string.error_required); ok = false
        }
        if (description.length < MIN_LENGTH) {
            binding.descriptionLayout.error = getString(R.string.error_required); ok = false
        }
        if (!ok) return

        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ServiceLocator.incidentRepository.report(
                IncidentCreateRequest(
                    vehicleId = vehicleId,
                    tripId = tripId,
                    title = title,
                    description = description,
                    severity = severity,
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    setFragmentResult(RESULT_KEY, bundleOf())
                    dismiss()
                }
                is ApiResult.Failure -> {
                    binding.titleLayout.error = result.error.fieldErrors["title"]
                    binding.descriptionLayout.error = result.error.fieldErrors["description"]
                    if (result.error.fieldErrors.isEmpty()) {
                        Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG)
                            .show()
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
        const val TAG = "IncidentFormSheet"
        const val RESULT_KEY = "incident_reported"
        private const val ARG_VEHICLE_ID = "vehicle_id"
        private const val ARG_VEHICLE_LABEL = "vehicle_label"
        private const val ARG_TRIP_ID = "trip_id"
        private const val DEFAULT_SEVERITY = "MEDIUM"
        private const val MIN_LENGTH = 3

        fun newInstance(vehicleId: String, vehicleLabel: String, tripId: String?) =
            IncidentFormSheet().apply {
                arguments = bundleOf(
                    ARG_VEHICLE_ID to vehicleId,
                    ARG_VEHICLE_LABEL to vehicleLabel,
                    ARG_TRIP_ID to tripId,
                )
            }
    }
}
