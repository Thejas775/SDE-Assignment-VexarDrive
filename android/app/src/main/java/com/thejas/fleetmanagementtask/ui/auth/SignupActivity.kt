package com.thejas.fleetmanagementtask.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.databinding.ActivitySignupBinding
import com.thejas.fleetmanagementtask.ui.common.formatUtcDate
import kotlinx.coroutines.launch

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: SignupViewModel by viewModels { SignupViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.signupRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindInputs()
        observe()
    }

    private fun bindInputs() = with(binding) {
        roleGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.onRoleChanged(
                if (checkedId == R.id.roleDriver) ROLE_DRIVER else ROLE_FLEET_MANAGER
            )
        }
        nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        passwordInput.doAfterTextChanged { viewModel.onPasswordChanged(it?.toString().orEmpty()) }
        confirmInput.doAfterTextChanged { viewModel.onConfirmChanged(it?.toString().orEmpty()) }
        phoneInput.doAfterTextChanged { viewModel.onPhoneChanged(it?.toString().orEmpty()) }
        licenceInput.doAfterTextChanged { viewModel.onLicenceChanged(it?.toString().orEmpty()) }

        expiryInput.setOnClickListener { pickExpiry() }
        signupButton.setOnClickListener { submit() }
        goToLoginButton.setOnClickListener { finish() }
    }

    /** A licence that has already expired is rejected by the API, so do not offer it. */
    private fun pickExpiry() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.driver_licence_expiry))
            .setCalendarConstraints(constraints)
            .build()
            .apply {
                addOnPositiveButtonClickListener { millis ->
                    val text = formatUtcDate(millis)
                    binding.expiryInput.setText(text)
                    viewModel.onExpiryChanged(text)
                }
            }
            .show(supportFragmentManager, "expiry")
    }

    private fun submit() = viewModel.submit(
        SignupStrings(
            nameRequired = getString(R.string.error_name_required),
            emailRequired = getString(R.string.error_email_required),
            emailInvalid = getString(R.string.error_email_invalid),
            passwordShort = getString(R.string.error_password_short),
            passwordsDiffer = getString(R.string.error_passwords_differ),
            phoneRequired = getString(R.string.error_phone_required),
            licenceRequired = getString(R.string.error_required),
            expiryRequired = getString(R.string.error_required),
        )
    )

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        with(binding) {
                            driverFields.visibility =
                                if (state.isDriver) View.VISIBLE else View.GONE
                            nameLayout.error = state.errors.name
                            emailLayout.error = state.errors.email
                            passwordLayout.error = state.errors.password
                            confirmLayout.error = state.errors.confirmPassword
                            phoneLayout.error = state.errors.phone
                            licenceLayout.error = state.errors.licenceNumber
                            expiryLayout.error = state.errors.licenceExpiry

                            signupProgress.visibility =
                                if (state.isSubmitting) View.VISIBLE else View.GONE
                            signupButton.isEnabled = !state.isSubmitting
                            signupButton.text =
                                if (state.isSubmitting) "" else getString(R.string.signup_submit)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SignupEvent.Registered -> goHome(event.role)
                            is SignupEvent.ShowMessage ->
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                    .show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun intent(activity: AppCompatActivity) = Intent(activity, SignupActivity::class.java)
    }
}
