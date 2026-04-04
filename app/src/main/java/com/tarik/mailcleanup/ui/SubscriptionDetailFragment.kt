package com.tarik.mailcleanup.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.FragmentSubscriptionDetailBinding
import java.util.Locale

class SubscriptionDetailFragment : Fragment() {

    companion object {
        private const val ARG_SENDER_NAME = "arg_sender_name"
        private const val ARG_SENDER_EMAIL = "arg_sender_email"
        private const val ARG_EMAIL_COUNT = "arg_email_count"

        fun createArgs(
            senderName: String,
            senderEmail: String,
            emailCount: Int
        ): Bundle {
            return Bundle().apply {
                putString(ARG_SENDER_NAME, senderName)
                putString(ARG_SENDER_EMAIL, senderEmail)
                putInt(ARG_EMAIL_COUNT, emailCount)
            }
        }
    }

    private var _binding: FragmentSubscriptionDetailBinding? = null
    private val binding get() = _binding!!

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
        bindData()
        setupClickListeners()
    }

    private fun bindData() {
        val senderName = arguments?.getString(ARG_SENDER_NAME).orEmpty().ifBlank {
            getString(R.string.unknown_sender)
        }
        val senderEmail = arguments?.getString(ARG_SENDER_EMAIL).orEmpty().ifBlank {
            getString(R.string.unknown_sender)
        }
        val emailCount = arguments?.getInt(ARG_EMAIL_COUNT) ?: 0
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
            parentFragmentManager.popBackStack()
        }

        binding.searchButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    SearchFragment::class.java,
                    null,
                    getString(R.string.fragment_tag_search)
                )
                .addToBackStack(getString(R.string.fragment_tag_search))
                .commit()
        }

        binding.unsubscribeButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.detail_unsubscribe_title))
                .setMessage(getString(R.string.detail_unsubscribe_message))
                .setNegativeButton(getString(R.string.dialog_option_cancel), null)
                .setPositiveButton(getString(R.string.detail_unsubscribe_confirm)) { _, _ ->
                    showMessage(getString(R.string.detail_action_queued))
                }
                .show()
        }

        binding.deleteAllButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.detail_delete_title))
                .setMessage(getString(R.string.detail_delete_message))
                .setNegativeButton(getString(R.string.dialog_option_cancel), null)
                .setPositiveButton(getString(R.string.detail_delete_confirm)) { _, _ ->
                    showMessage(getString(R.string.detail_action_queued))
                }
                .show()
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
