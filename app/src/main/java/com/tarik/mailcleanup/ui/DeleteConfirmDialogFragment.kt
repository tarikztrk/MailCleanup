package com.tarik.mailcleanup.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.DialogDeleteConfirmBinding

class DeleteConfirmDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_EMAIL_COUNT = "arg_email_count"
        const val REQUEST_KEY = "delete_confirm_request"
        const val RESULT_CONFIRMED = "result_confirmed"

        fun newInstance(emailCount: Int): DeleteConfirmDialogFragment {
            return DeleteConfirmDialogFragment().apply {
                arguments = bundleOf(ARG_EMAIL_COUNT to emailCount)
            }
        }
    }

    private var _binding: DialogDeleteConfirmBinding? = null
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
        _binding = DialogDeleteConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val emailCount = arguments?.getInt(ARG_EMAIL_COUNT) ?: 0
        binding.descriptionTextView.text = getString(R.string.delete_modal_desc_format, emailCount)

        binding.overlayView.setOnClickListener { dismissAllowingStateLoss() }
        binding.cancelButton.setOnClickListener { dismissAllowingStateLoss() }
        binding.deleteAllButton.setOnClickListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_CONFIRMED to true)
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
