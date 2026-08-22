package com.thejas.fleetmanagementtask.ui.placeholder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.thejas.fleetmanagementtask.R
import com.thejas.fleetmanagementtask.databinding.FragmentPlaceholderBinding

/** Temporary tabs; each is replaced by its real screen in a later part. */
abstract class PlaceholderFragment(@StringRes private val titleRes: Int) : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        binding.placeholderText.text =
            getString(R.string.coming_soon, getString(titleRes))
        return binding.root
    }
}

