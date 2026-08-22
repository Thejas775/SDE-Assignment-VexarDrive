package com.thejas.fleetmanagementtask

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.databinding.ActivityMainBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.auth.LoginActivity
import kotlinx.coroutines.launch

/** Placeholder home screen; the role-specific dashboards replace it next. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.roleText.text = getString(R.string.home_role, ServiceLocator.tokenStore.role ?: "-")
        binding.signOutButton.setOnClickListener { signOut() }

        loadProfile()
        observeSessionExpiry()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            when (val result = ServiceLocator.authRepository.me()) {
                is ApiResult.Success ->
                    binding.welcomeText.text =
                        getString(R.string.home_signed_in_as, result.data.fullName)
                is ApiResult.Failure ->
                    Snackbar.make(binding.main, result.error.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    /** A rejected refresh token means the session is gone; go back to login. */
    private fun observeSessionExpiry() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.sessionExpired.collect { returnToLogin() }
            }
        }
    }

    private fun signOut() {
        binding.signOutButton.isEnabled = false
        lifecycleScope.launch {
            ServiceLocator.authRepository.logout()
            returnToLogin()
        }
    }

    private fun returnToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
