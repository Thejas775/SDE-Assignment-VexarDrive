package com.thejas.fleetmanagementtask.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.remote.dto.RegisterRequest
import com.thejas.fleetmanagementtask.data.repository.AuthRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

const val ROLE_DRIVER = "DRIVER"

data class SignupForm(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phone: String = "",
    val licenceNumber: String = "",
    val licenceExpiry: String = "",
    val role: String = ROLE_DRIVER,
)

data class SignupErrors(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null,
    val phone: String? = null,
    val licenceNumber: String? = null,
    val licenceExpiry: String? = null,
) {
    val any: Boolean
        get() = listOfNotNull(
            name, email, password, confirmPassword, phone, licenceNumber, licenceExpiry
        ).isNotEmpty()
}

/** Messages the caller supplies, so the ViewModel stays free of Android types. */
data class SignupStrings(
    val nameRequired: String,
    val emailRequired: String,
    val emailInvalid: String,
    val passwordShort: String,
    val passwordsDiffer: String,
    val phoneRequired: String,
    val licenceRequired: String,
    val expiryRequired: String,
)

data class SignupUiState(
    val form: SignupForm = SignupForm(),
    val errors: SignupErrors = SignupErrors(),
    val isSubmitting: Boolean = false,
) {
    val isDriver: Boolean get() = form.role == ROLE_DRIVER
}

sealed interface SignupEvent {
    data class Registered(val fullName: String, val role: String) : SignupEvent
    data class ShowMessage(val message: String) : SignupEvent
}

class SignupViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(SignupUiState())
    val state: StateFlow<SignupUiState> = _state.asStateFlow()

    private val _events = Channel<SignupEvent>(Channel.BUFFERED)
    val events: Flow<SignupEvent> = _events.receiveAsFlow()

    fun onRoleChanged(role: String) = update { copy(role = role) }
    fun onNameChanged(value: String) = update { copy(fullName = value) }
    fun onEmailChanged(value: String) = update { copy(email = value) }
    fun onPasswordChanged(value: String) = update { copy(password = value) }
    fun onConfirmChanged(value: String) = update { copy(confirmPassword = value) }
    fun onPhoneChanged(value: String) = update { copy(phone = value) }
    fun onLicenceChanged(value: String) = update { copy(licenceNumber = value) }
    fun onExpiryChanged(value: String) = update { copy(licenceExpiry = value) }

    private inline fun update(block: SignupForm.() -> SignupForm) {
        _state.value = _state.value.copy(
            form = _state.value.form.block(),
            errors = SignupErrors(),
        )
    }

    fun submit(strings: SignupStrings) {
        val current = _state.value
        if (current.isSubmitting) return

        val errors = validate(current.form, strings)
        if (errors.any) {
            _state.value = current.copy(errors = errors)
            return
        }

        _state.value = current.copy(isSubmitting = true, errors = SignupErrors())
        viewModelScope.launch {
            val form = current.form
            val result = auth.signUp(
                RegisterRequest(
                    email = form.email.trim().lowercase(),
                    password = form.password,
                    fullName = form.fullName.trim(),
                    phoneNumber = form.phone.trim().ifBlank { null },
                    role = form.role,
                    licenseNumber = form.licenceNumber.takeIf { current.isDriver }
                        ?.let { value -> value.filterNot { it.isWhitespace() }.uppercase() },
                    licenseExpiry = form.licenceExpiry.takeIf { current.isDriver },
                )
            )
            when (result) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(isSubmitting = false)
                    _events.send(SignupEvent.Registered(result.data.fullName, result.data.role))
                }
                is ApiResult.Failure -> {
                    val fields = result.error.fieldErrors
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        errors = SignupErrors(
                            name = fields["full_name"],
                            email = fields["email"],
                            password = fields["password"],
                            phone = fields["phone_number"],
                            licenceNumber = fields["license_number"],
                            licenceExpiry = fields["license_expiry"],
                        ),
                    )
                    // A duplicate email or licence is a 409 with no field to blame.
                    if (fields.isEmpty()) {
                        _events.send(SignupEvent.ShowMessage(result.error.message))
                    }
                }
            }
        }
    }

    private fun validate(form: SignupForm, strings: SignupStrings): SignupErrors {
        val driver = form.role == ROLE_DRIVER
        return SignupErrors(
            name = strings.nameRequired.takeIf { form.fullName.isBlank() },
            email = when {
                form.email.isBlank() -> strings.emailRequired
                !isEmail(form.email) -> strings.emailInvalid
                else -> null
            },
            password = strings.passwordShort.takeIf { form.password.length < MIN_PASSWORD },
            // Only flag the mismatch once the password itself is acceptable,
            // so the user does not see two errors for one mistake.
            confirmPassword = strings.passwordsDiffer.takeIf {
                form.password.length >= MIN_PASSWORD && form.confirmPassword != form.password
            },
            phone = strings.phoneRequired.takeIf { driver && form.phone.isBlank() },
            licenceNumber = strings.licenceRequired.takeIf { driver && form.licenceNumber.isBlank() },
            licenceExpiry = strings.expiryRequired.takeIf { driver && form.licenceExpiry.isBlank() },
        )
    }

    private fun isEmail(value: String) =
        android.util.Patterns.EMAIL_ADDRESS?.matcher(value.trim())?.matches()
            ?: value.matches(FALLBACK_EMAIL)

    companion object {
        const val MIN_PASSWORD = 8
        private val FALLBACK_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        val Factory = viewModelFactory {
            initializer { SignupViewModel(ServiceLocator.authRepository) }
        }
    }
}
