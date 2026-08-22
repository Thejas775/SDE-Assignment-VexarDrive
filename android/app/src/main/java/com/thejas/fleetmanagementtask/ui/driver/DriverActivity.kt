package com.thejas.fleetmanagementtask.ui.driver

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.databinding.FragmentPlaceholderBinding

/** Driver home lands in a later part; the manager flow comes first. */
class DriverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = FragmentPlaceholderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.placeholderText.text = getString(R.string.coming_soon, "Driver home")
    }
}
