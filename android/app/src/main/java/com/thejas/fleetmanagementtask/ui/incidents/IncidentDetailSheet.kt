package com.thejas.fleetmanagementtask.ui.incidents

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.IncidentDto
import com.thejas.fleetmanagementtask.databinding.DialogResolutionBinding
import com.thejas.fleetmanagementtask.databinding.SheetIncidentDetailBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.auth.ROLE_FLEET_MANAGER
import com.thejas.fleetmanagementtask.ui.common.formatInstant
import kotlinx.coroutines.launch
import com.thejas.fleetmanagementtask.ui.common.applyStatus

class IncidentDetailSheet : BottomSheetDialogFragment() {

    private var _binding: SheetIncidentDetailBinding? = null
    private val binding get() = _binding!!
    private val repository get() = ServiceLocator.incidentRepository
    private val incidentId: String get() = requireArguments().getString(ARG_ID)!!
    private val isManager get() = ServiceLocator.tokenStore.role == ROLE_FLEET_MANAGER

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetIncidentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()
    }

    private fun load() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = repository.byId(incidentId)) {
            is ApiResult.Success -> bind(result.data)
            is ApiResult.Failure -> {
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    private fun bind(incident: IncidentDto) {
        binding.incidentTitle.text = incident.title
        binding.severityChip.applyStatus(incident.severity)
        binding.vehicleText.text = incident.vehicle.label
        binding.metaText.text = listOfNotNull(
            FleetEnums.label(incident.status),
            getString(R.string.incident_reported_by, incident.reportedBy.fullName),
            formatInstant(incident.reportedAt),
            incident.assignedTo?.let { "Assigned to ${it.fullName}" },
        ).joinToString(" · ")
        binding.descriptionText.text = incident.description
        binding.resolutionText.text = incident.resolutionNotes.orEmpty()
        binding.resolutionText.visibility =
            if (incident.resolutionNotes.isNullOrBlank()) View.GONE else View.VISIBLE

        // A driver may read their own report but not act on it.
        val canAct = isManager && incident.isOpen
        binding.assignButton.visibility =
            if (canAct && incident.assignedTo == null) View.VISIBLE else View.GONE
        binding.resolveButton.visibility = if (canAct) View.VISIBLE else View.GONE
        binding.assignButton.setOnClickListener { assignToMe() }
        binding.resolveButton.setOnClickListener { askResolution(incident) }
    }

    private fun assignToMe() {
        val userId = ServiceLocator.tokenStore.userId ?: return
        binding.assignButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.assign(incidentId, userId)) {
                is ApiResult.Success -> {
                    bind(result.data)
                    notifyChanged()
                }
                is ApiResult.Failure ->
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
            }
            binding.assignButton.isEnabled = true
        }
    }

    /** The API refuses to resolve without a note, so the UI asks for one. */
    private fun askResolution(incident: IncidentDto) {
        val dialogBinding = DialogResolutionBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.incident_resolve))
            .setMessage(incident.title)
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.incident_resolve, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val notes = dialogBinding.resolutionInput.text.toString().trim()
                if (notes.isBlank()) {
                    dialogBinding.resolutionLayout.error =
                        getString(R.string.incident_resolution_required)
                    return@setOnClickListener
                }
                dialog.dismiss()
                resolve(notes)
            }
        }
        dialog.show()
    }

    private fun resolve(notes: String) = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = repository.setStatus(incidentId, "RESOLVED", notes)) {
            is ApiResult.Success -> {
                bind(result.data)
                notifyChanged()
            }
            is ApiResult.Failure ->
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun notifyChanged() =
        setFragmentResult(IncidentListFragment.RESULT_KEY, bundleOf())

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "IncidentDetailSheet"
        private const val ARG_ID = "incident_id"

        fun newInstance(id: String) = IncidentDetailSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
