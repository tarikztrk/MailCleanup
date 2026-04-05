package com.tarik.mailcleanup.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.FragmentSubscriptionDetailBinding
import java.util.Locale
import kotlinx.coroutines.launch

class SubscriptionDetailFragment : Fragment() {

    companion object {
        private const val PREFS_NAME = "mail_cleanup_ui_prefs"
        private const val PREF_SKIP_UNSUBSCRIBE_CONFIRM = "pref_skip_unsubscribe_confirm"
    }

    private var _binding: FragmentSubscriptionDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val args: SubscriptionDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDialogResults()
        observeUiEvents()
        bindData()
        setupClickListeners()
    }

    private fun bindData() {
        val senderName = args.senderName.ifBlank {
            getString(R.string.unknown_sender)
        }
        val senderEmail = args.senderEmail.ifBlank {
            getString(R.string.unknown_sender)
        }
        val emailCount = args.emailCount
        val weeklyFrequencyValue = (emailCount / 48.0).coerceAtLeast(0.1)
        val weeklyFrequency = "~${String.format(Locale.US, "%.1f", weeklyFrequencyValue)} emails per week"
        val storageMb = (emailCount * 0.29).toInt().coerceAtLeast(1)

        binding.subscriptionNameTextView.text = senderName
        binding.subscriptionEmailTextView.text = senderEmail
        binding.totalEmailsValueTextView.text = emailCount.toString()
        binding.storageSavedValueTextView.text = storageMb.toString()
        binding.frequencyValueTextView.text = weeklyFrequency
        binding.deleteItemsCountTextView.text = getString(R.string.detail_items_count, emailCount)
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.searchButton.setOnClickListener {
            val action = SubscriptionDetailFragmentDirections
                .actionSubscriptionDetailFragmentToSearchFragment()
            findNavController().navigate(action)
        }

        binding.navHomeItem.setOnClickListener {
            findNavController().popBackStack(R.id.subscriptionListFragment, false)
        }

        binding.navActivityItem.setOnClickListener {
            showMessage(getString(R.string.nav_activity_selected))
        }

        binding.navSettingsItem.setOnClickListener {
            showMessage(getString(R.string.nav_settings_soon))
        }

        binding.unsubscribeButton.setOnClickListener {
            if (shouldSkipUnsubscribeConfirm()) {
                viewModel.unsubscribeFromDetail(
                    senderName = requireSenderName(),
                    senderEmail = requireSenderEmail(),
                    cleanEmails = false
                )
                return@setOnClickListener
            }
            UnsubscribeConfirmDialogFragment.newInstance(requireSenderName())
                .show(parentFragmentManager, "unsubscribe_confirm_dialog")
        }

        binding.deleteAllButton.setOnClickListener {
            val emailCount = args.emailCount
            DeleteConfirmDialogFragment.newInstance(emailCount)
                .show(parentFragmentManager, "delete_confirm_dialog")
        }
    }

    private fun setupDialogResults() {
        parentFragmentManager.setFragmentResultListener(
            UnsubscribeConfirmDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val confirmed = bundle.getBoolean(UnsubscribeConfirmDialogFragment.RESULT_CONFIRMED, false)
            if (!confirmed) return@setFragmentResultListener

            val dontShowAgain = bundle.getBoolean(
                UnsubscribeConfirmDialogFragment.RESULT_DONT_SHOW_AGAIN,
                false
            )
            if (dontShowAgain) {
                saveSkipUnsubscribeConfirm(true)
            }
            viewModel.unsubscribeFromDetail(
                senderName = requireSenderName(),
                senderEmail = requireSenderEmail(),
                cleanEmails = false
            )
        }

        parentFragmentManager.setFragmentResultListener(
            DeleteConfirmDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val confirmed = bundle.getBoolean(DeleteConfirmDialogFragment.RESULT_CONFIRMED, false)
            if (!confirmed) return@setFragmentResultListener
            viewModel.deleteAllEmailsFromDetail(requireSenderEmail())
        }
    }

    private fun observeUiEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvent.collect { event ->
                    when (event) {
                        is MainUiEvent.ShowMessage -> showMessage(event.message)
                        is MainUiEvent.ShowError -> showMessage(event.message)
                        is MainUiEvent.OpenUrl -> openUrlInCustomTab(event.url)
                        is MainUiEvent.ShowUndo -> Unit
                    }
                }
            }
        }
    }

    private fun shouldSkipUnsubscribeConfirm(): Boolean {
        return requireContext()
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .getBoolean(PREF_SKIP_UNSUBSCRIBE_CONFIRM, false)
    }

    private fun saveSkipUnsubscribeConfirm(skip: Boolean) {
        requireContext()
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SKIP_UNSUBSCRIBE_CONFIRM, skip)
            .apply()
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun openUrlInCustomTab(url: String) {
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(requireContext(), android.net.Uri.parse(url))
        }.onFailure {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun requireSenderName(): String {
        return args.senderName.ifBlank {
            getString(R.string.unknown_sender)
        }
    }

    private fun requireSenderEmail(): String {
        return args.senderEmail.ifBlank {
            getString(R.string.unknown_sender)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
