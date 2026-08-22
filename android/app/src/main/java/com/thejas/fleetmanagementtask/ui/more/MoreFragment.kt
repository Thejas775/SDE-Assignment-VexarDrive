package com.thejas.fleetmanagementtask.ui.more

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.thejas.fleetmanagementtask.core.ApiResult
import com.thejas.fleetmanagementtask.databinding.FragmentMoreBinding
import com.thejas.fleetmanagementtask.di.ServiceLocator
import com.thejas.fleetmanagementtask.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class MoreFragment : Fragment() {

    private var _binding: FragmentMoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.profileRole.text = ServiceLocator.tokenStore.role.orEmpty()
        binding.signOutButton.setOnClickListener { signOut() }

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = ServiceLocator.authRepository.me()) {
                is ApiResult.Success -> {
                    binding.profileName.text = result.data.fullName
                    binding.profileEmail.text = result.data.email
                }
                is ApiResult.Failure ->
                    Snackbar.make(binding.root, result.error.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun signOut() {
        binding.signOutButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            ServiceLocator.authRepository.logout()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
