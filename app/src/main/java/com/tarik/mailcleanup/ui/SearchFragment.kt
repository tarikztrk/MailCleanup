package com.tarik.mailcleanup.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.FragmentSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: SearchResultAdapter
    private var pagesUpdatedListener: (() -> Unit)? = null
    private var searchTextWatcher: TextWatcher? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        observeData()
    }

    private fun setupUi() {
        adapter = SearchResultAdapter { subscription ->
            Snackbar.make(binding.root, getString(R.string.search_opening_result, subscription.senderName), Snackbar.LENGTH_SHORT).show()
        }
        binding.searchResultsRecyclerView.adapter = adapter
        pagesUpdatedListener = {
            updateMatchCount()
        }
        adapter.addOnPagesUpdatedListener(pagesUpdatedListener!!)

        binding.searchEditText.setText(viewModel.uiState.value.searchQuery)
        binding.searchEditText.setSelection(binding.searchEditText.text?.length ?: 0)

        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
                updateMatchCount()
            }
        }
        binding.searchEditText.addTextChangedListener(searchTextWatcher)

        binding.clearButton.setOnClickListener {
            binding.searchEditText.setText("")
        }

        binding.backButton.setOnClickListener {
            viewModel.setSearchQuery("")
            parentFragmentManager.popBackStack()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedSubscriptions.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }
            }
        }
    }

    private fun updateMatchCount() {
        val safeBinding = _binding ?: return
        safeBinding.matchCountTextView.text = getString(R.string.search_match_count_format, adapter.itemCount)
    }

    override fun onDestroyView() {
        searchTextWatcher?.let { watcher ->
            _binding?.searchEditText?.removeTextChangedListener(watcher)
        }
        searchTextWatcher = null

        pagesUpdatedListener?.let { listener ->
            adapter.removeOnPagesUpdatedListener(listener)
        }
        pagesUpdatedListener = null

        _binding?.searchResultsRecyclerView?.adapter = null
        super.onDestroyView()
        _binding = null
    }
}
