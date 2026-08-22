package com.thejas.fleetmanagementtask.ui.trips

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
import com.google.android.material.timepicker.MaterialTimePicker
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.data.remote.dto.TripCreateRequest
import com.thejas.fleetmanagementtask.data.repository.DriverFilters
import com.thejas.fleetmanagementtask.databinding.SheetTripFormBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.common.formatInstant
import com.thejas.fleetmanagementtask.ui.common.toUtcIso
import kotlinx.coroutines.launch

class TripFormSheet : BottomSheetDialogFragment() {

    private var _binding: SheetTripFormBinding? = null
    private val binding get() = _binding!!

    private var drivers: List<DriverDto> = emptyList()
    private var selectedDriver: DriverDto? = null
    private var startIso: String? = null
    private var endIso: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetTripFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.startInput.setOnClickListener {
            pickDateTime(R.string.trip_scheduled_start) { iso ->
                startIso = iso
                binding.startInput.setText(formatInstant(iso))
                binding.startLayout.error = null
            }
        }
        binding.endInput.setOnClickListener {
            pickDateTime(R.string.trip_scheduled_end) { iso ->
                endIso = iso
                binding.endInput.setText(formatInstant(iso))
                binding.endLayout.error = null
            }
        }
        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { dismiss() }
        loadDrivers()
    }

    /**
     * The API only accepts a trip whose driver already holds that vehicle, so
     * the vehicle is derived from the driver rather than chosen freely.
     */
    private fun loadDrivers() = viewLifecycleOwner.lifecycleScope.launch {
        when (val result = ServiceLocator.driverRepository.page(
            page = 1,
            filters = DriverFilters(status = "ACTIVE"),
            pageSize = 100,
        )) {
            is ApiResult.Success -> {
                drivers = result.data.items
                binding.driverInput.setAdapter(
                    ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_list_item_1,
                        drivers.map { it.fullName },
                    )
                )
                binding.driverInput.setOnItemClickListener { _, _, position, _ ->
                    onDriverPicked(drivers[position])
                }
            }
            is ApiResult.Failure ->
                Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun onDriverPicked(driver: DriverDto) {
        selectedDriver = driver
        binding.driverLayout.error = null
        val vehicle = driver.assignedVehicle
        binding.vehicleInput.setText(vehicle?.label.orEmpty())
        binding.vehicleLayout.error =
            if (vehicle == null) getString(R.string.trip_no_vehicle) else null
    }

    private fun pickDateTime(titleRes: Int, onPicked: (String) -> Unit) {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(titleRes))
            .build()
            .apply {
                addOnPositiveButtonClickListener { dateMillis ->
                    val time = MaterialTimePicker.Builder()
                        .setTitleText(getString(titleRes))
                        .setHour(9)
                        .build()
                    time.addOnPositiveButtonClickListener {
                        onPicked(toUtcIso(dateMillis, time.hour, time.minute))
                    }
                    time.show(childFragmentManager, "time")
                }
            }
            .show(childFragmentManager, "date")
    }

    private fun save() {
        binding.conflictCard.visibility = View.GONE
        val driver = selectedDriver
        val vehicleId = driver?.assignedVehicle?.id
        var ok = true

        if (driver == null) {
            binding.driverLayout.error = getString(R.string.error_required)
            ok = false
        } else if (vehicleId == null) {
            binding.vehicleLayout.error = getString(R.string.trip_no_vehicle)
            ok = false
        }
        if (binding.sourceInput.text.isNullOrBlank()) {
            binding.sourceLayout.error = getString(R.string.error_required); ok = false
        }
        if (binding.destinationInput.text.isNullOrBlank()) {
            binding.destinationLayout.error = getString(R.string.error_required); ok = false
        }
        if (startIso == null) {
            binding.startLayout.error = getString(R.string.error_required); ok = false
        }
        if (endIso == null) {
            binding.endLayout.error = getString(R.string.error_required); ok = false
        }
        if (!ok) return

        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ServiceLocator.tripRepository.create(
                TripCreateRequest(
                    vehicleId = vehicleId!!,
                    driverId = driver!!.id,
                    source = binding.sourceInput.text.toString(),
                    destination = binding.destinationInput.text.toString(),
                    scheduledStart = startIso!!,
                    scheduledEnd = endIso!!,
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    setFragmentResult(TripListFragment.RESULT_KEY, bundleOf())
                    dismiss()
                }
                is ApiResult.Failure -> {
                    // A double-booked driver or a missing assignment is a 409 that
                    // names the clashing trip; keep it on screen.
                    if (result.error.isConflict) {
                        binding.conflictText.text = result.error.message
                        binding.conflictCard.visibility = View.VISIBLE
                    } else {
                        binding.startLayout.error = result.error.fieldErrors["scheduled_start"]
                        binding.endLayout.error = result.error.fieldErrors["scheduled_end"]
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
        const val TAG = "TripFormSheet"
    }
}
