package com.thejas.fleetmanagementtask.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.data.repository.AuthRepository
import com.thejas.fleetmanagementtask.di.ServiceLocator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
) {
    val canSubmit: Boolean get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()
}

sealed interface LoginEvent {
    data class LoggedIn(val fullName: String, val role: String) : LoginEvent
    data class ShowMessage(val message: String) : LoginEvent
}

/** Field-level messages the server returns are matched back to their input. */
private val EMAIL_FIELDS = setOf("email")
private val PASSWORD_FIELDS = setOf("password")

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    val isAlreadySignedIn: Boolean get() = auth.isLoggedIn

    fun onEmailChanged(value: String) {
        _state.value = _state.value.copy(email = value, emailError = null)
    }

    fun onPasswordChanged(value: String) {
        _state.value = _state.value.copy(password = value, passwordError = null)
    }

    fun submit(
        emailRequired: String,
        emailInvalid: String,
        passwordRequired: String,
    ) {
        val current = _state.value
        if (current.isSubmitting) return

        val emailError = when {
            current.email.isBlank() -> emailRequired
            !isEmail(current.email) -> emailInvalid
            else -> null
        }
        val passwordError = if (current.password.isBlank()) passwordRequired else null
        if (emailError != null || passwordError != null) {
            _state.value = current.copy(emailError = emailError, passwordError = passwordError)
            return
        }

        _state.value = current.copy(isSubmitting = true, emailError = null, passwordError = null)
        viewModelScope.launch {
            when (val result = auth.login(current.email, current.password)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(isSubmitting = false)
                    _events.send(LoginEvent.LoggedIn(result.data.fullName, result.data.role))
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    _state.value = _state.value.copy(
                        isSubmitting = false,
                        emailError = error.fieldErrors.firstMatching(EMAIL_FIELDS),
                        passwordError = error.fieldErrors.firstMatching(PASSWORD_FIELDS),
                    )
                    // A 401 has no field to blame, so it belongs in the snackbar.
                    if (error.fieldErrors.isEmpty()) {
                        _events.send(LoginEvent.ShowMessage(error.message))
                    }
                }
            }
        }
    }

    private fun Map<String, String>.firstMatching(names: Set<String>): String? =
        entries.firstOrNull { it.key in names }?.value

    private fun isEmail(value: String) =
        android.util.Patterns.EMAIL_ADDRESS?.matcher(value.trim())?.matches()
            ?: value.matches(FALLBACK_EMAIL)

    companion object {
        // Patterns is an Android framework class and is null under JVM unit tests.
        private val FALLBACK_EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        val Factory = viewModelFactory {
            initializer { LoginViewModel(ServiceLocator.authRepository) }
        }
    }
}
