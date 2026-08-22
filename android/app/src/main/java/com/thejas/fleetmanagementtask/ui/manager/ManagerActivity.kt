package com.thejas.fleetmanagementtask.ui.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.thejas.fleetmanagementtask.databinding.ActivityManagerBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class ManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManagerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.managerRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val navController = (supportFragmentManager
            .findFragmentById(binding.navHost.id) as NavHostFragment).navController
        binding.bottomNav.setupWithNavController(navController)

        observeSessionExpiry()
    }

    /** A rejected refresh token means the session is gone; go back to login. */
    private fun observeSessionExpiry() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.sessionExpired.collect {
                    startActivity(
                        Intent(this@ManagerActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
            }
        }
    }
}
