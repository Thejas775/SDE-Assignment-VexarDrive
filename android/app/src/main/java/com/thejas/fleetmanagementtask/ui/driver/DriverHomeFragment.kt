package com.thejas.fleetmanagementtask.ui.driver

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.core.LocationProvider
import com.thejas.fleetmanagementtask.core.latitudeString
import com.thejas.fleetmanagementtask.core.longitudeString
import com.thejas.fleetmanagementtask.databinding.DialogOdometerBinding
import com.thejas.fleetmanagementtask.databinding.FragmentDriverHomeBinding
import com.thejas.fleetmanagementtask.service.LocationTrackingService
import com.thejas.fleetmanagementtask.ui.incidents.IncidentFormSheet
import com.thejas.fleetmanagementtask.ui.common.formatInstant
import kotlinx.coroutines.launch

class DriverHomeFragment : Fragment() {

    private var _binding: FragmentDriverHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DriverHomeViewModel by viewModels { DriverHomeViewModel.Factory }
    private lateinit var location: LocationProvider

    private val requestLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { pendingAction?.invoke() }

    /** The action waiting on a permission answer, run either way. */
    private var pendingAction: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDriverHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        location = LocationProvider(requireContext().applicationContext)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        childFragmentManager.setFragmentResultListener(
            IncidentFormSheet.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            Snackbar.make(binding.root, R.string.incident_reported, Snackbar.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.events.collect(::handle) }
            }
        }
    }

    private fun render(state: DriverHomeUiState) {
        binding.swipeRefresh.isRefreshing = state.isLoading
        syncTrackingWith(state)

        state.vehicle?.let { vehicle ->
            binding.vehicleRegistration.text = vehicle.registrationNumber
            binding.vehicleDetails.text =
                "${vehicle.make} ${vehicle.model} · ${vehicle.year} · ${FleetEnums.label(vehicle.fuelType)}"
            binding.vehicleMileage.text = "${vehicle.currentMileage} km"
        } ?: run {
            binding.vehicleRegistration.setText(R.string.driver_no_vehicle)
            binding.vehicleDetails.text = ""
            binding.vehicleMileage.text = ""
        }

        // Reporting needs a vehicle to report against.
        binding.reportIssueButton.visibility =
            if (state.vehicle != null) View.VISIBLE else View.GONE
        binding.reportIssueButton.setOnClickListener {
            val vehicle = state.vehicle ?: return@setOnClickListener
            IncidentFormSheet.newInstance(
                vehicleId = vehicle.id,
                vehicleLabel = "${vehicle.registrationNumber} · ${vehicle.make} ${vehicle.model}",
                tripId = state.trip?.takeIf { it.isActive }?.id,
            ).show(childFragmentManager, IncidentFormSheet.TAG)
        }

        binding.tripCard.visibility = if (state.hasTrip) View.VISIBLE else View.GONE
        binding.emptyTrip.visibility = if (state.hasTrip) View.GONE else View.VISIBLE

        state.trip?.let { trip ->
            binding.tripHeader.setText(
                if (trip.isActive) R.string.driver_current_trip else R.string.driver_next_trip
            )
            binding.tripNumber.text = trip.tripNumber
            binding.tripRoute.text = trip.route
            binding.tripStatus.text = FleetEnums.label(trip.status)
            binding.tripSchedule.text =
                "${formatInstant(trip.scheduledStart)} → ${formatInstant(trip.scheduledEnd)}"
            binding.tripProgress.visibility =
                if (trip.startOdometer != null) View.VISIBLE else View.GONE
            binding.tripProgress.text =
                "Started ${formatInstant(trip.actualStart)} · odometer ${trip.startOdometer}"

            binding.tripActionButton.isEnabled = !state.isWorking
            when {
                state.canStart -> {
                    binding.tripActionButton.visibility = View.VISIBLE
                    binding.tripActionButton.setText(R.string.trip_start)
                    binding.tripActionButton.setOnClickListener { askOdometer(start = true) }
                }
                state.canComplete -> {
                    binding.tripActionButton.visibility = View.VISIBLE
                    binding.tripActionButton.setText(R.string.trip_complete)
                    binding.tripActionButton.setOnClickListener { askOdometer(start = false) }
                }
                else -> binding.tripActionButton.visibility = View.GONE
            }
        }
    }

    private fun handle(event: DriverHomeEvent) = when (event) {
        is DriverHomeEvent.Started -> {
            LocationTrackingService.start(requireContext().applicationContext, event.trip.id)
            Snackbar.make(binding.root, R.string.trip_started, Snackbar.LENGTH_SHORT).show()
        }
        is DriverHomeEvent.Completed -> {
            LocationTrackingService.stop(requireContext().applicationContext)
            Snackbar.make(
                binding.root,
                getString(R.string.trip_completed, event.trip.distanceKm ?: "-"),
                Snackbar.LENGTH_LONG,
            ).show()
        }
        is DriverHomeEvent.Message ->
            Snackbar.make(binding.root, event.text, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Reconnects tracking after the app is killed and reopened mid-trip:
     * startForegroundService on an already-running service is a no-op.
     */
    private fun syncTrackingWith(state: DriverHomeUiState) {
        val trip = state.trip ?: return
        if (trip.isActive) {
            LocationTrackingService.start(requireContext().applicationContext, trip.id)
        }
    }

    private fun askOdometer(start: Boolean) {
        val state = viewModel.state.value
        val trip = state.trip ?: return
        val dialogBinding = DialogOdometerBinding.inflate(layoutInflater)
        // Prefill from the last known reading so the driver confirms rather than types.
        val suggested = if (start) {
            state.vehicle?.currentMileage ?: trip.startOdometer
        } else {
            trip.startOdometer
        }
        dialogBinding.odometerInput.setText(suggested?.toString().orEmpty())
        dialogBinding.odometerLayout.setHint(
            if (start) R.string.trip_start_odometer else R.string.trip_end_odometer
        )
        dialogBinding.locationHint.text =
            if (location.hasPermission()) "" else getString(R.string.location_permission_rationale)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                getString(
                    if (start) R.string.trip_start_title else R.string.trip_complete_title,
                    trip.tripNumber,
                )
            )
            .setView(dialogBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(if (start) R.string.trip_start else R.string.trip_complete, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val odometer = dialogBinding.odometerInput.text.toString().toIntOrNull()
                if (odometer == null) {
                    dialogBinding.odometerLayout.error = getString(R.string.error_required)
                    return@setOnClickListener
                }
                dialog.dismiss()
                withLocation { latitude, longitude ->
                    if (start) {
                        viewModel.start(odometer, latitude, longitude)
                    } else {
                        viewModel.complete(odometer, latitude, longitude)
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Takes a fix if permitted, but never blocks the trip on it: a driver in a
     * basement must still be able to start and finish work.
     */
    private fun withLocation(action: (String?, String?) -> Unit) {
        if (!location.hasPermission()) {
            pendingAction = { fetchThen(action) }
            requestLocation.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
            return
        }
        fetchThen(action)
    }

    private fun fetchThen(action: (String?, String?) -> Unit) {
        pendingAction = null
        viewLifecycleOwner.lifecycleScope.launch {
            val fix = location.current()
            if (fix == null && location.hasPermission()) {
                Snackbar.make(binding.root, R.string.location_unavailable, Snackbar.LENGTH_SHORT)
                    .show()
            }
            action(fix?.latitudeString(), fix?.longitudeString())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
