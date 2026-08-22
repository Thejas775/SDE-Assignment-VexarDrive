package com.thejas.fleetmanagementtask.ui.drivers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.DriverCreateRequest
import com.thejas.fleetmanagementtask.data.remote.dto.DriverDto
import com.thejas.fleetmanagementtask.data.remote.dto.DriverUpdateRequest
import com.thejas.fleetmanagementtask.databinding.SheetDriverFormBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.common.formatUtcDate
import kotlinx.coroutines.launch

class DriverFormSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDriverFormBinding? = null
    private val binding get() = _binding!!
    private val repository get() = ServiceLocator.driverRepository
    private val driverId: String? get() = arguments?.getString(ARG_ID)
    private val isEdit get() = driverId != null
    private var existing: DriverDto? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SheetDriverFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.formTitle.setText(if (isEdit) R.string.driver_edit else R.string.drivers_add)

        // Credentials are set once, at creation; editing them is a separate concern.
        val credentialsVisible = if (isEdit) View.GONE else View.VISIBLE
        binding.emailLayout.visibility = credentialsVisible
        binding.passwordLayout.visibility = credentialsVisible

        binding.expiryInput.setOnClickListener {
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.driver_licence_expiry))
                .build()
                .apply {
                    addOnPositiveButtonClickListener { binding.expiryInput.setText(formatUtcDate(it)) }
                }
                .show(childFragmentManager, "expiry")
        }
        clearErrorsOnType()
        binding.saveButton.setOnClickListener { save() }
        binding.cancelButton.setOnClickListener { dismiss() }

        if (isEdit) loadExisting()
    }

    private fun loadExisting() {
        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = repository.byId(driverId!!)) {
                is ApiResult.Success -> {
                    existing = result.data
                    binding.nameInput.setText(result.data.fullName)
                    binding.phoneInput.setText(result.data.phoneNumber)
                    binding.licenceInput.setText(result.data.licenseNumber)
                    binding.expiryInput.setText(result.data.licenseExpiry)
                    setEnabled(true)
                }
                is ApiResult.Failure -> {
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
                    dismiss()
                }
            }
        }
    }

    private fun save() {
        if (!validate()) return
        setEnabled(false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (isEdit) {
                repository.update(driverId!!, buildUpdate())
            } else {
                repository.create(
                    DriverCreateRequest(
                        email = binding.emailInput.text.toString().trim().lowercase(),
                        password = binding.passwordInput.text.toString(),
                        fullName = binding.nameInput.text.toString(),
                        phoneNumber = binding.phoneInput.text.toString(),
                        licenseNumber = binding.licenceInput.text.toString(),
                        licenseExpiry = binding.expiryInput.text.toString(),
                    )
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    setFragmentResult(DriverListFragment.RESULT_KEY, bundleOf())
                    dismiss()
                }
                is ApiResult.Failure -> {
                    val errors = result.error.fieldErrors
                    binding.emailLayout.error = errors["email"]
                    binding.passwordLayout.error = errors["password"]
                    binding.nameLayout.error = errors["full_name"]
                    binding.phoneLayout.error = errors["phone_number"]
                    binding.licenceLayout.error = errors["license_number"]
                    binding.expiryLayout.error = errors["license_expiry"]
                    if (errors.isEmpty()) {
                        // Duplicate email or licence arrives as a 409 with no field.
                        Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG)
                            .show()
                    }
                    setEnabled(true)
                }
            }
        }
    }

    private fun buildUpdate(): DriverUpdateRequest {
        val before = existing
        fun changed(new: String, old: String?): String? = if (new != old) new else null
        return DriverUpdateRequest(
            fullName = changed(binding.nameInput.text.toString(), before?.fullName),
            phoneNumber = changed(binding.phoneInput.text.toString(), before?.phoneNumber),
            licenseNumber = changed(binding.licenceInput.text.toString(), before?.licenseNumber),
            licenseExpiry = changed(binding.expiryInput.text.toString(), before?.licenseExpiry),
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
        require(binding.nameLayout, binding.nameInput.text.toString())
        require(binding.phoneLayout, binding.phoneInput.text.toString())
        require(binding.licenceLayout, binding.licenceInput.text.toString())
        require(binding.expiryLayout, binding.expiryInput.text.toString())
        if (!isEdit) {
            require(binding.emailLayout, binding.emailInput.text.toString())
            val password = binding.passwordInput.text.toString()
            if (password.length < MIN_PASSWORD) {
                binding.passwordLayout.error = getString(R.string.error_password_short)
                ok = false
            }
        }
        return ok
    }

    private fun clearErrorsOnType() = with(binding) {
        nameInput.doAfterTextChanged { nameLayout.error = null }
        emailInput.doAfterTextChanged { emailLayout.error = null }
        passwordInput.doAfterTextChanged { passwordLayout.error = null }
        phoneInput.doAfterTextChanged { phoneLayout.error = null }
        licenceInput.doAfterTextChanged { licenceLayout.error = null }
        expiryInput.doAfterTextChanged { expiryLayout.error = null }
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
        const val TAG = "DriverFormSheet"
        private const val ARG_ID = "driver_id"
        private const val MIN_PASSWORD = 8

        fun newInstance(id: String?) = DriverFormSheet().apply {
            arguments = bundleOf(ARG_ID to id)
        }
    }
}
