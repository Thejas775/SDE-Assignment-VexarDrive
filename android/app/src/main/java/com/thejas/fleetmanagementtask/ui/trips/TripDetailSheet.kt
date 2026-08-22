package com.thejas.fleetmanagementtask.ui.trips

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
import com.thejas.fleetmanagementtask.data.remote.dto.TripDto
import com.thejas.fleetmanagementtask.databinding.SheetTripDetailBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.common.formatInstant
import kotlinx.coroutines.launch

class TripDetailSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTripDetailBinding? = null
    private val binding get() = _binding!!
    private val repository get() = ServiceLocator.tripRepository
    private val tripId: String get() = requireArguments().getString(ARG_ID)!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetTripDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load()
        loadRoute()
    }

    private fun load() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = repository.byId(tripId)) {
            is ApiResult.Success -> bind(result.data)
            is ApiResult.Failure -> {
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
                dismiss()
            }
        }
    }

    private fun loadRoute() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = repository.route(tripId)) {
            is ApiResult.Success -> binding.routeSummary.text = if (result.data.isEmpty()) {
                getString(R.string.trip_route_none)
            } else {
                getString(R.string.trip_route_points, result.data.size)
            }
            is ApiResult.Failure -> binding.routeSummary.text = getString(R.string.trip_route_none)
        }
    }

    private fun bind(trip: TripDto) {
        binding.tripNumber.text = trip.tripNumber
        binding.tripRoute.text = trip.route
        binding.tripStatus.text = FleetEnums.label(trip.status)
        binding.tripAssignment.text =
            "${trip.vehicle.label} · ${trip.driver.fullName}"
        binding.plannedValue.text =
            "${formatInstant(trip.scheduledStart)} → ${formatInstant(trip.scheduledEnd)}"
        // A scheduled trip has no actuals at all; show a dash rather than blanks.
        binding.actualValue.text = if (trip.actualStart == null) {
            "-"
        } else {
            "${formatInstant(trip.actualStart)} → ${formatInstant(trip.actualEnd)}"
        }
        binding.odometerValue.text = if (trip.startOdometer == null) {
            "-"
        } else {
            "${trip.startOdometer} → ${trip.endOdometer ?: "-"}"
        }
        binding.distanceValue.text = trip.distanceKm?.let { "$it km" } ?: "-"
        binding.durationValue.text = trip.durationMinutes?.let { "$it min" } ?: "-"
        binding.notesText.text = trip.notes.orEmpty()
        binding.notesText.visibility = if (trip.notes.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.cancelTripButton.visibility = if (trip.isCancellable) View.VISIBLE else View.GONE
        binding.cancelTripButton.setOnClickListener { confirmCancel(trip) }
    }

    private fun confirmCancel(trip: TripDto) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.trip_cancel)
            .setMessage("${trip.tripNumber} · ${trip.route}")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.trip_cancel) { _, _ -> cancelTrip() }
            .show()
    }

    private fun cancelTrip() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = repository.cancel(tripId, reason = null)) {
            is ApiResult.Success -> {
                bind(result.data)
                setFragmentResult(TripListFragment.RESULT_KEY, bundleOf())
            }
            is ApiResult.Failure ->
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "TripDetailSheet"
        private const val ARG_ID = "trip_id"

        fun newInstance(id: String) = TripDetailSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
