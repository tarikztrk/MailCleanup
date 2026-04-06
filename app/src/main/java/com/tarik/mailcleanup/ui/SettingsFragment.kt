package com.tarik.mailcleanup.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.menuButton.setOnClickListener { showSoonMessage() }
        binding.editAvatarButton.setOnClickListener { showSoonMessage() }
        binding.themeRow.setOnClickListener { showSoonMessage() }
        binding.managePlanRow.setOnClickListener { showSoonMessage() }
        binding.restorePurchaseRow.setOnClickListener { showSoonMessage() }
        binding.contactUsRow.setOnClickListener { showSoonMessage() }
        binding.privacyPolicyRow.setOnClickListener { showSoonMessage() }
        binding.termsOfServiceRow.setOnClickListener { showSoonMessage() }
        binding.logoutButton.setOnClickListener { showSoonMessage() }

        binding.notificationsSwitch.setOnCheckedChangeListener { _, _ ->
            showSoonMessage()
        }

        binding.navCurateItem.setOnClickListener {
            findNavController().popBackStack(R.id.subscriptionListFragment, false)
        }
    }

    private fun showSoonMessage() {
        showSnackbar(getString(R.string.settings_coming_soon))
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
