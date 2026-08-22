package com.thejas.fleetmanagementtask.ui.auth

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.isAlreadySignedIn) {
            goHome(viewModel.storedRole)
            return
        }

        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets()
        bindInputs()
        observe()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun bindInputs() {
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged {
            viewModel.onPasswordChanged(it?.toString().orEmpty())
        }
        binding.loginButton.setOnClickListener { submit() }
        binding.passwordInput.setOnEditorActionListener { _, _, _ -> submit(); true }
        binding.goToSignupButton.setOnClickListener {
            startActivity(SignupActivity.intent(this))
        }
    }

    private fun submit() = viewModel.submit(
        emailRequired = getString(R.string.error_email_required),
        emailInvalid = getString(R.string.error_email_invalid),
        passwordRequired = getString(R.string.error_password_required),
    )

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        binding.emailLayout.error = state.emailError
                        binding.passwordLayout.error = state.passwordError
                        binding.loginProgress.isVisible(state.isSubmitting)
                        binding.loginButton.isEnabled = !state.isSubmitting
                        binding.loginButton.text =
                            if (state.isSubmitting) "" else getString(R.string.login_submit)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LoginEvent.LoggedIn -> goHome(event.role)
                            is LoginEvent.ShowMessage ->
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                                    .show()
                        }
                    }
                }
            }
        }
    }

    private fun android.view.View.isVisible(visible: Boolean) {
        visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }
}
