package com.thejas.fleetmanagementtask.ui.vehicles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.core.FleetEnums
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleDto
import com.thejas.fleetmanagementtask.data.remote.dto.VehicleUpdateRequest
import com.thejas.fleetmanagementtask.databinding.SheetVehicleFormBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.common.formatUtcDate
import kotlinx.coroutines.launch

class VehicleFormSheet : BottomSheetDialogFragment() {

    private var _binding: SheetVehicleFormBinding? = null
    private val binding get() = _binding!!
    private val repository get() = ServiceLocator.vehicleRepository
    private val vehicleId: String? get() = arguments?.getString(ARG_ID)
    private val isEdit get() = vehicleId != null
    private var existing: VehicleDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetVehicleFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.formTitle.setText(if (isEdit) R.string.vehicle_edit else R.string.vehicles_add)

        bindDropdown(binding.typeInput, FleetEnums.vehicleTypes)
        bindDropdown(binding.fuelInput, FleetEnums.fuelTypes)
        if (isEdit) bindDropdown(binding.statusInput, FleetEnums.editableStatuses)
        binding.statusLayout.visibility = if (isEdit) View.VISIBLE else View.GONE

        binding.insuranceInput.setOnClickListener { pickDate(R.string.vehicle_insurance) { binding.insuranceInput.setText(it) } }
        binding.registrationExpiryInput.setOnClickListener { pickDate(R.string.vehicle_registration_expiry) { binding.registrationExpiryInput.setText(it) } }
        clearErrorsOnType()

        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { dismiss() }

        if (isEdit) loadExisting()
    }

    private fun bindDropdown(view: android.widget.AutoCompleteTextView, values: List<String>) {
        val labels = values.map(FleetEnums::label)
        view.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        view.setOnItemClickListener { _, _, position, _ -> view.tag = values[position] }
    }

    private fun selected(view: android.widget.AutoCompleteTextView): String? = view.tag as? String

    private fun pickDate(titleRes: Int, onPicked: (String) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(titleRes))
            .build()
        picker.addOnPositiveButtonClickListener { millis -> onPicked(formatUtcDate(millis)) }
        picker.show(childFragmentManager, "date")
    }

    private fun loadExisting() {
        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.byId(vehicleId!!)) {
                is ApiResult.Success -> {
                    existing = result.data
                    fill(result.data)
                    setEnabled(true)
                }
                is ApiResult.Failure -> {
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
                    dismiss()
                }
            }
        }
    }

    private fun fill(data: VehicleDto) = with(binding) {
        registrationInput.setText(data.registrationNumber)
        makeInput.setText(data.make)
        modelInput.setText(data.model)
        yearInput.setText(data.year.toString())
        mileageInput.setText(data.currentMileage.toString())
        insuranceInput.setText(data.insuranceExpiry)
        registrationExpiryInput.setText(data.registrationExpiry)
        typeInput.setText(FleetEnums.label(data.vehicleType), false)
        typeInput.tag = data.vehicleType
        fuelInput.setText(FleetEnums.label(data.fuelType), false)
        fuelInput.tag = data.fuelType
        if (data.status in FleetEnums.editableStatuses) {
            statusInput.setText(FleetEnums.label(data.status), false)
            statusInput.tag = data.status
        }
    }

    private fun save() {
        if (!validate()) return
        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (isEdit) {
                repository.update(vehicleId!!, buildUpdate())
            } else {
                repository.create(buildCreate())
            }
            when (result) {
                is ApiResult.Success -> {
                    setFragmentResult(VehicleListFragment.RESULT_KEY, bundleOf())
                    dismiss()
                }
                is ApiResult.Failure -> {
                    showServerErrors(result.error.fieldErrors)
                    if (result.error.fieldErrors.isEmpty()) {
                        Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG)
                            .show()
                    }
                    setEnabled(true)
                }
            }
        }
    }

    private fun buildCreate() = VehicleCreateRequest(
        registrationNumber = binding.registrationInput.text.toString(),
        vehicleType = selected(binding.typeInput)!!,
        make = binding.makeInput.text.toString(),
        model = binding.modelInput.text.toString(),
        year = binding.yearInput.text.toString().toInt(),
        fuelType = selected(binding.fuelInput)!!,
        currentMileage = binding.mileageInput.text.toString().toIntOrNull() ?: 0,
        insuranceExpiry = binding.insuranceInput.text.toString(),
        registrationExpiry = binding.registrationExpiryInput.text.toString(),
    )

    /** Only changed fields are sent; PUT here is a partial update. */
    private fun buildUpdate(): VehicleUpdateRequest {
        val before = existing
        fun <T> changed(new: T, old: T?): T? = if (new != old) new else null
        return VehicleUpdateRequest(
            registrationNumber = changed(binding.registrationInput.text.toString(), before?.registrationNumber),
            vehicleType = changed(selected(binding.typeInput), before?.vehicleType),
            make = changed(binding.makeInput.text.toString(), before?.make),
            model = changed(binding.modelInput.text.toString(), before?.model),
            year = changed(binding.yearInput.text.toString().toIntOrNull(), before?.year),
            fuelType = changed(selected(binding.fuelInput), before?.fuelType),
            currentMileage = changed(binding.mileageInput.text.toString().toIntOrNull(), before?.currentMileage),
            status = changed(selected(binding.statusInput), before?.status),
            insuranceExpiry = changed(binding.insuranceInput.text.toString(), before?.insuranceExpiry),
            registrationExpiry = changed(binding.registrationExpiryInput.text.toString(), before?.registrationExpiry),
        )
    }

    private fun validate(): Boolean {
        var ok = true
        fun require(layout: com.google.android.material.textfield.TextInputLayout, value: String) {
            if (value.isBlank()) {
                layout.error = getString(R.string.error_required)
                ok = false
            }
        }
        require(binding.registrationLayout, binding.registrationInput.text.toString())
        require(binding.makeLayout, binding.makeInput.text.toString())
        require(binding.modelLayout, binding.modelInput.text.toString())
        require(binding.insuranceLayout, binding.insuranceInput.text.toString())
        require(binding.registrationExpiryLayout, binding.registrationExpiryInput.text.toString())

        val year = binding.yearInput.text.toString().toIntOrNull()
        if (year == null || year < 1900 || year > 2100) {
            binding.yearLayout.error = getString(R.string.error_year_range)
            ok = false
        }
        if (selected(binding.typeInput) == null) {
            binding.typeLayout.error = getString(R.string.error_required)
            ok = false
        }
        if (selected(binding.fuelInput) == null) {
            binding.fuelLayout.error = getString(R.string.error_required)
            ok = false
        }
        return ok
    }

    /** Server field names map onto the inputs that produced them. */
    private fun showServerErrors(fieldErrors: Map<String, String>) {
        binding.registrationLayout.error = fieldErrors["registration_number"]
        binding.makeLayout.error = fieldErrors["make"]
        binding.modelLayout.error = fieldErrors["model"]
        binding.yearLayout.error = fieldErrors["year"]
        binding.mileageLayout.error = fieldErrors["current_mileage"]
        binding.insuranceLayout.error = fieldErrors["insurance_expiry"]
        binding.registrationExpiryLayout.error = fieldErrors["registration_expiry"]
    }

    private fun clearErrorsOnType() = with(binding) {
        registrationInput.doAfterTextChanged { registrationLayout.error = null }
        makeInput.doAfterTextChanged { makeLayout.error = null }
        modelInput.doAfterTextChanged { modelLayout.error = null }
        yearInput.doAfterTextChanged { yearLayout.error = null }
        mileageInput.doAfterTextChanged { mileageLayout.error = null }
        insuranceInput.doAfterTextChanged { insuranceLayout.error = null }
        registrationExpiryInput.doAfterTextChanged { registrationExpiryLayout.error = null }
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
        const val TAG = "VehicleFormSheet"
        private const val ARG_ID = "vehicle_id"

        fun newInstance(id: String?) = VehicleFormSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
