package com.tarik.mailcleanup.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.DialogUnsubscribeConfirmBinding

class UnsubscribeConfirmDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_SENDER_NAME = "arg_sender_name"
        const val REQUEST_KEY = "unsubscribe_confirm_request"
        const val RESULT_CONFIRMED = "result_confirmed"
        const val RESULT_DONT_SHOW_AGAIN = "result_dont_show_again"

        fun newInstance(senderName: String): UnsubscribeConfirmDialogFragment {
            return UnsubscribeConfirmDialogFragment().apply {
                arguments = bundleOf(ARG_SENDER_NAME to senderName)
            }
        }
    }

    private var _binding: DialogUnsubscribeConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.ThemeOverlay_MailCleanup_ModalDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUnsubscribeConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val senderName = arguments?.getString(ARG_SENDER_NAME).orEmpty().ifBlank {
            getString(R.string.unknown_sender)
        }

        binding.titleTextView.text = getString(R.string.unsubscribe_modal_title_format, senderName)

        binding.overlayView.setOnClickListener { dismissAllowingStateLoss() }
        binding.keepButton.setOnClickListener { dismissAllowingStateLoss() }
        binding.unsubscribeButton.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    RESULT_CONFIRMED to true,
                    RESULT_DONT_SHOW_AGAIN to binding.dontShowAgainCheckBox.isChecked
                )
            )
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
