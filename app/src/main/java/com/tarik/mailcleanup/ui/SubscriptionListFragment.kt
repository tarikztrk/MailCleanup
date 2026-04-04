package com.tarik.mailcleanup.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.EditText
import android.view.animation.AlphaAnimation
import android.view.animation.LinearInterpolator
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.LoadState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.FragmentSubscriptionListBinding
import com.tarik.mailcleanup.domain.model.DomainError
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.ui.paging.DomainPagingException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Abonelik listesini render eden ekran.
 * Paging stream'i dinler, seçim modunu yönetir ve UI event'lerini tüketir.
 */
@AndroidEntryPoint
class SubscriptionListFragment : Fragment() {
    companion object {
        private const val SORT_MENU_MOST_FREQUENT = 1001
        private const val SORT_MENU_A_TO_Z = 1002
    }

    private var _binding: FragmentSubscriptionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var subscriptionAdapter: SubscriptionAdapter
    private var currentAccount: MailAccount? = null

    private var hasShownAllLoadedMessage = false
    private var actionMode: ActionMode? = null
    private var lastSelectedEmails: Set<String> = emptySet()
    private var isSkeletonAnimating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentAccount = viewModel.currentMailAccount()
        setupRecyclerView()
        setupDashboardControls()
        observeViewModel()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedCount = viewModel.getSelectedItemsCount()
            if (selectedCount == 0) return false

            when (item.itemId) {
                R.id.action_unsubscribe -> {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.unsubscribe_dialog_title))
                        .setMessage(getString(R.string.unsubscribe_dialog_message, selectedCount.toString()))
                        .setNegativeButton(getString(R.string.dialog_option_unsubscribe_only)) { _, _ ->
                            viewModel.bulkUnsubscribeSelected(currentAccount, cleanEmails = false)
                            mode.finish()
                        }
                        .setPositiveButton(getString(R.string.dialog_option_unsubscribe_and_clean)) { _, _ ->
                            viewModel.bulkUnsubscribeSelected(currentAccount, cleanEmails = true)
                            mode.finish()
                        }
                        .setNeutralButton(getString(R.string.dialog_option_cancel), null)
                        .show()
                    return true
                }

                R.id.action_select_all -> {
                    viewModel.selectAll(subscriptionAdapter.snapshot().items)
                    return true
                }

                else -> return false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            viewModel.clearSelection()
        }
    }

    private fun setupRecyclerView() {
        subscriptionAdapter = SubscriptionAdapter(
            clickListener = { subscription ->
                if (viewModel.uiState.value.isSelectionMode) {
                    viewModel.toggleSelection(subscription)
                }
            },
            longClickListener = { subscription ->
                if (!viewModel.uiState.value.isSelectionMode) {
                    viewModel.startSelectionMode(subscription)
                }
                true
            },
            isSelectionMode = { viewModel.uiState.value.isSelectionMode },
            isSelected = { subscription -> viewModel.uiState.value.selectedItems.contains(subscription) },
            onUnsubscribeClicked = { subscription ->
                showUnsubscribeConfirmationDialog(subscription)
            }
        )

        binding.subscriptionsRecyclerView.adapter = subscriptionAdapter
    }

    private fun setupDashboardControls() {
        binding.searchButton.setOnClickListener {
            showSearchDialog()
        }

        binding.sortControl.setOnClickListener { anchor ->
            showSortMenu(anchor)
        }

        binding.filterAllChip.setOnClickListener {
            viewModel.setFilter(SubscriptionFilter.ALL)
        }
        binding.filterNewslettersChip.setOnClickListener {
            viewModel.setFilter(SubscriptionFilter.NEWSLETTERS)
        }
        binding.filterPromotionsChip.setOnClickListener {
            viewModel.setFilter(SubscriptionFilter.PROMOTIONS)
        }

        binding.bottomNavBar.getChildAt(0)?.setOnClickListener {
            showSnackbar(getString(R.string.nav_home_selected))
        }
        binding.bottomNavBar.getChildAt(1)?.setOnClickListener {
            showSnackbar(getString(R.string.nav_activity_soon))
        }
        binding.bottomNavBar.getChildAt(2)?.setOnClickListener {
            showSnackbar(getString(R.string.nav_settings_soon))
        }

    }

    private fun showSearchDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.search_hint)
            setText(viewModel.uiState.value.searchQuery)
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.search_cd))
            .setView(input)
            .setPositiveButton(getString(R.string.search_apply)) { _, _ ->
                viewModel.setSearchQuery(input.text?.toString().orEmpty())
            }
            .setNeutralButton(getString(R.string.search_clear)) { _, _ ->
                viewModel.setSearchQuery("")
            }
            .setNegativeButton(getString(R.string.dialog_option_cancel), null)
            .show()
    }

    private fun showSortMenu(anchor: View) {
        val popupMenu = PopupMenu(requireContext(), anchor)
        popupMenu.menu.add(0, SORT_MENU_MOST_FREQUENT, 0, getString(R.string.sort_by_most_frequent))
        popupMenu.menu.add(0, SORT_MENU_A_TO_Z, 1, getString(R.string.sort_by_a_to_z))
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                SORT_MENU_MOST_FREQUENT -> viewModel.setSort(SubscriptionSort.MOST_FREQUENT)
                SORT_MENU_A_TO_Z -> viewModel.setSort(SubscriptionSort.A_TO_Z)
            }
            true
        }
        popupMenu.show()
    }

    private fun renderFilterChips(filter: SubscriptionFilter) {
        binding.filterAllChip.setTextColor(
            resources.getColor(
                if (filter == SubscriptionFilter.ALL) android.R.color.white else R.color.secondary_text,
                null
            )
        )
        binding.filterNewslettersChip.setTextColor(
            resources.getColor(
                if (filter == SubscriptionFilter.NEWSLETTERS) android.R.color.white else R.color.secondary_text,
                null
            )
        )
        binding.filterPromotionsChip.setTextColor(
            resources.getColor(
                if (filter == SubscriptionFilter.PROMOTIONS) android.R.color.white else R.color.secondary_text,
                null
            )
        )

        binding.filterAllChip.setBackgroundResource(
            if (filter == SubscriptionFilter.ALL) R.drawable.bg_filter_chip_active_selector else R.drawable.bg_filter_chip_inactive_selector
        )
        binding.filterNewslettersChip.setBackgroundResource(
            if (filter == SubscriptionFilter.NEWSLETTERS) R.drawable.bg_filter_chip_active_selector else R.drawable.bg_filter_chip_inactive_selector
        )
        binding.filterPromotionsChip.setBackgroundResource(
            if (filter == SubscriptionFilter.PROMOTIONS) R.drawable.bg_filter_chip_active_selector else R.drawable.bg_filter_chip_inactive_selector
        )
    }

    private fun renderSort(sort: SubscriptionSort) {
        val text = when (sort) {
            SubscriptionSort.MOST_FREQUENT -> getString(R.string.sort_by_most_frequent)
            SubscriptionSort.A_TO_Z -> getString(R.string.sort_by_a_to_z)
        }
        binding.sortTextView.text = text
    }

    private fun showUnsubscribeConfirmationDialog(subscription: Subscription) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.unsubscribe_dialog_title))
            .setMessage(getString(R.string.unsubscribe_dialog_message, subscription.senderName))
            .setNegativeButton(getString(R.string.dialog_option_unsubscribe_only)) { _, _ ->
                currentAccount?.let { viewModel.unsubscribeAndClean(it, subscription, cleanEmails = false) }
            }
            .setPositiveButton(getString(R.string.dialog_option_unsubscribe_and_clean)) { _, _ ->
                currentAccount?.let { viewModel.unsubscribeAndClean(it, subscription, cleanEmails = true) }
            }
            .setNeutralButton(getString(R.string.dialog_option_cancel), null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagedSubscriptions.collectLatest { pagingData ->
                        subscriptionAdapter.submitData(pagingData)
                    }
                }

                launch {
                    subscriptionAdapter.loadStateFlow.collectLatest { loadState ->
                        val isRefreshing = loadState.refresh is LoadState.Loading
                        val isAppending = loadState.append is LoadState.Loading
                        val isEmpty = !isRefreshing && subscriptionAdapter.itemCount == 0
                        val endReached = loadState.append is LoadState.NotLoading &&
                            (loadState.append as LoadState.NotLoading).endOfPaginationReached

                        binding.loadMoreProgressBar.visibility = if (isAppending) View.VISIBLE else View.GONE
                        val showSkeleton = isRefreshing && subscriptionAdapter.itemCount == 0
                        binding.initialLoadingOverlay.visibility = if (showSkeleton) View.VISIBLE else View.GONE
                        if (showSkeleton) startSkeletonPulse() else stopSkeletonPulse()
                        checkEmptyState(isEmpty)

                        if (endReached &&
                            subscriptionAdapter.itemCount > 0 &&
                            !hasShownAllLoadedMessage
                        ) {
                            hasShownAllLoadedMessage = true
                            showSnackbar(getString(R.string.snackbar_all_loaded))
                        }

                        if (!endReached) {
                            hasShownAllLoadedMessage = false
                        }

                        val errorState = when {
                            loadState.refresh is LoadState.Error -> loadState.refresh as LoadState.Error
                            loadState.append is LoadState.Error -> loadState.append as LoadState.Error
                            else -> null
                        }

                        errorState?.let {
                            Log.e("SubscriptionListFragment", "Paging error", it.error)
                            // DomainPagingException varsa kullanıcıya doğru hata sınıfını gösterir.
                            val message = (it.error as? DomainPagingException)
                                ?.let { domainException -> domainErrorToMessage(domainException.domainError) }
                                ?: getString(R.string.error_generic)
                            showSnackbar(message, isError = true)
                        }
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        currentAccount = viewModel.currentMailAccount()
                        subscriptionAdapter.setProcessingState(state.processingEmail)
                        val currentSelectedEmails = state.selectedItems.map { it.senderEmail }.toSet()
                        if (currentSelectedEmails != lastSelectedEmails) {
                            // Sadece değişen satırlar yeniden çizilir.
                            subscriptionAdapter.notifySelectionChanged(lastSelectedEmails, currentSelectedEmails)
                            lastSelectedEmails = currentSelectedEmails
                        }

                        if (state.scanStatus is ScanUiStatus.InProgress) {
                            binding.emptyStateLayout.visibility = View.GONE
                            binding.subscriptionsRecyclerView.visibility = View.VISIBLE
                        }
                        renderFilterChips(state.selectedFilter)
                        renderSort(state.selectedSort)

                        if (state.isSelectionMode && actionMode == null) {
                            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
                        } else if (!state.isSelectionMode && actionMode != null) {
                            actionMode?.finish()
                        }
                        actionMode?.title = getString(R.string.selection_title, state.selectedItems.size)
                    }
                }

                launch {
                    viewModel.uiEvent.collect { event ->
                        // Event'ler tek seferlik tüketilir (state içinde tutulmaz).
                        when (event) {
                            is MainUiEvent.ShowUndo -> showUndoSnackbar(event.message)
                            is MainUiEvent.ShowError -> showSnackbar(event.message, isError = true)
                            is MainUiEvent.ShowMessage -> showSnackbar(event.message)
                            is MainUiEvent.OpenUrl -> openUrlInCustomTab(event.url)
                        }
                    }
                }
            }
        }
    }

    private fun checkEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.subscriptionsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.subscriptionsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun showUndoSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.snackbar_undo)) {
                    viewModel.undoLastAction()
                }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            viewModel.finalizePendingAction()
                        }
                    }
                })
                .show()
        }
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        view?.let {
            val snackbar = Snackbar.make(it, message, Snackbar.LENGTH_LONG)
            if (isError) {
                snackbar.setBackgroundTint(resources.getColor(com.google.android.material.R.color.design_default_color_error, null))
            }
            snackbar.show()
        }
    }

    private fun openUrlInCustomTab(url: String) {
        try {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
        } catch (e: Exception) {
            Log.e("SubscriptionListFragment", "Custom Tab acilamadi, standart intent kullaniliyor.", e)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun domainErrorToMessage(error: DomainError): String {
        return when (error) {
            DomainError.Generic -> getString(R.string.error_generic)
            DomainError.Auth -> getString(R.string.error_auth)
            DomainError.RateLimit -> getString(R.string.error_rate_limit)
            DomainError.Network -> getString(R.string.error_network)
            DomainError.Server -> getString(R.string.error_server)
            DomainError.NoUnsubscribeMethod -> getString(R.string.error_no_unsubscribe_method)
            DomainError.KeepFailed -> getString(R.string.error_keep_failed)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSkeletonPulse()
        lastSelectedEmails = emptySet()
        _binding = null
    }

    private fun startSkeletonPulse() {
        if (isSkeletonAnimating) return
        val animation = AlphaAnimation(1f, 0.55f).apply {
            duration = 900
            repeatMode = AlphaAnimation.REVERSE
            repeatCount = AlphaAnimation.INFINITE
            interpolator = LinearInterpolator()
        }
        binding.initialLoadingOverlay.startAnimation(animation)
        isSkeletonAnimating = true
    }

    private fun stopSkeletonPulse() {
        if (!isSkeletonAnimating || _binding == null) return
        binding.initialLoadingOverlay.clearAnimation()
        isSkeletonAnimating = false
    }
}
