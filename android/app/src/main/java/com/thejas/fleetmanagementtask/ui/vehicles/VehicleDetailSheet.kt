package com.thejas.fleetmanagementtask.ui.vehicles

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.databinding.SheetVehicleDetailBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.launch
import com.thejas.fleetmanagementtask.ui.common.applyStatus

class VehicleDetailSheet : BottomSheetDialogFragment() {

    private var _binding: SheetVehicleDetailBinding? = null
    private val binding get() = _binding!!
    private val repository get() = ServiceLocator.vehicleRepository
    private val vehicleId: String get() = requireArguments().getString(ARG_ID)!!
    private var vehicle: VehicleDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetVehicleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editButton.setOnClickListener {
            VehicleFormSheet.newInstance(vehicleId)
                .show(parentFragmentManager, VehicleFormSheet.TAG)
            dismiss()
        }
        binding.toggleActiveButton.setOnClickListener { toggleActive() }
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.byId(vehicleId)) {
                is ApiResult.Success -> bind(result.data)
                is ApiResult.Failure -> {
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
                    dismiss()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.qrPng(vehicleId)) {
                is ApiResult.Success -> {
                    val bitmap = BitmapFactory.decodeByteArray(result.data, 0, result.data.size)
                    binding.qrImage.setImageBitmap(bitmap)
                    binding.qrImage.visibility = View.VISIBLE
                }
                // The QR is a nicety; failing to fetch it must not break the sheet.
                is ApiResult.Failure -> binding.qrImage.visibility = View.GONE
            }
        }
    }

    private fun bind(data: VehicleDto) {
        vehicle = data
        binding.registrationText.text = data.registrationNumber
        binding.detailsText.text = "${data.make} ${data.model} · ${data.year}"
        binding.statusChip.applyStatus(data.status)
        binding.typeValue.text = FleetEnums.label(data.vehicleType)
        binding.fuelValue.text = FleetEnums.label(data.fuelType)
        binding.mileageValue.text = "${data.currentMileage} km"
        binding.insuranceValue.text = data.insuranceExpiry
        binding.registrationValue.text = data.registrationExpiry
        binding.insuranceWarning.visibility =
            if (data.insuranceExpiringSoon) View.VISIBLE else View.GONE
        binding.registrationWarning.visibility =
            if (data.registrationExpiringSoon) View.VISIBLE else View.GONE
        binding.toggleActiveButton.setText(
            if (data.status == "INACTIVE") R.string.vehicle_activate else R.string.vehicle_deactivate
        )
    }

    private fun toggleActive() {
        val current = vehicle ?: return
        binding.toggleActiveButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.setActive(vehicleId, current.status == "INACTIVE")) {
                is ApiResult.Success -> {
                    bind(result.data)
                    notifyChanged()
                }
                is ApiResult.Failure ->
                    // A vehicle on a trip cannot be deactivated; the server says why.
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
            }
            binding.toggleActiveButton.isEnabled = true
        }
    }

    /** Tells the list to reload; the sheet is shown from its child manager. */
    private fun notifyChanged() =
        setFragmentResult(VehicleListFragment.RESULT_KEY, bundleOf())

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "VehicleDetailSheet"
        private const val ARG_ID = "vehicle_id"

        fun newInstance(id: String) = VehicleDetailSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
