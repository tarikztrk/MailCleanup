package com.tarik.mailcleanup.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.databinding.FragmentSubscriptionListBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubscriptionListFragment : Fragment() {

    private var _binding: FragmentSubscriptionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var subscriptionAdapter: SubscriptionAdapter
    private var currentAccount: GoogleSignInAccount? = null
    
    // --- YENİ: Listenin sonuna gelinip gelinmediğini takip edecek bayrak ---
    private var isLastPage = false
    private var hasShownAllLoadedMessage = false
    private var actionMode: ActionMode? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        setupRecyclerView()
        setupMenu() // YENİ: Menüyü kur
        observeViewModel()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedCount = viewModel.getSelectedItemsCount()
            if (selectedCount == 0) return false

            when (item.itemId) {
                R.id.action_unsubscribe -> {
                    // Toplu çıkış için de bir onay diyaloğu gösterelim
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.unsubscribe_dialog_title))
                        .setMessage(getString(R.string.unsubscribe_dialog_message, selectedCount.toString()))
                        .setNegativeButton(getString(R.string.dialog_option_unsubscribe_only)) { _, _ ->
                            viewModel.bulkUnsubscribeSelected(currentAccount, cleanEmails = false)
                            mode.finish() // İşlem başlatıldığı için modu kapat
                        }
                        .setPositiveButton(getString(R.string.dialog_option_unsubscribe_and_clean)) { _, _ ->
                            viewModel.bulkUnsubscribeSelected(currentAccount, cleanEmails = true)
                            mode.finish() // İşlem başlatıldığı için modu kapat
                        }
                        .setNeutralButton(getString(R.string.dialog_option_cancel), null)
                        .show()
                    return true
                }
                R.id.action_keep -> {
                    viewModel.bulkKeepSelected()
                    mode.finish() // İşlem başlatıldığı için modu kapat
                    return true
                }
                R.id.action_select_all -> {
                    viewModel.selectAll()
                    return true
                }
                else -> return false
            }
        }
        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            viewModel.clearSelection() // Mod kapandığında seçimi temizle
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
            isSelected = { subscription ->
                viewModel.uiState.value.selectedItems.contains(subscription)
            },
            onUnsubscribeClicked = { subscription ->
                showUnsubscribeConfirmationDialog(subscription)
            },
            onKeepClicked = { subscription ->
                viewModel.keepSubscription(subscription)
            }
        )
        binding.subscriptionsRecyclerView.adapter = subscriptionAdapter
        
        // Sonsuz kaydırma için scroll listener ekle
        binding.subscriptionsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // --- YENİ KONTROL: Eğer son sayfaya ulaşıldıysa, daha fazla istek gönderme ---
                if (isLastPage) {
                    return
                }

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
                
                if (totalItemCount > 0 && lastVisibleItemPosition >= totalItemCount - 5) {
                    // ViewModel'e daha fazla veri yüklemesi için sinyal gönder
                    currentAccount?.let { account ->
                        viewModel.loadMoreSubscriptions(account)
                    }
                }
            }
        })
    }
    
    // --- YENİ FONKSİYON: Arama Menüsünü Kurma ---
    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.main_menu, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem.actionView as SearchView

                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        // Kullanıcı Enter'a bastığında
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        // Kullanıcı her harf girdiğinde
                        viewModel.setSearchQuery(newText.orEmpty())
                        return true
                    }
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                // Diğer menü öğeleri için
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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
            viewModel.uiState.collect { state ->
                subscriptionAdapter.submitList(state.filteredSubscriptions)
                subscriptionAdapter.setProcessingState(state.processingEmail)
                checkEmptyState(state.filteredSubscriptions)

                binding.loadMoreProgressBar.visibility = if (state.isLoadingMore) View.VISIBLE else View.GONE
                isLastPage = state.isLastPage

                if (state.isLastPage && !hasShownAllLoadedMessage) {
                    hasShownAllLoadedMessage = true
                    showSnackbar(getString(R.string.snackbar_all_loaded))
                } else if (!state.isLastPage) {
                    hasShownAllLoadedMessage = false
                }

                if (state.scanStatus is ScanUiStatus.InProgress) {
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.subscriptionsRecyclerView.visibility = View.VISIBLE
                }
                if (state.isSelectionMode && actionMode == null) {
                    actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
                } else if (!state.isSelectionMode && actionMode != null) {
                    actionMode?.finish()
                }
                actionMode?.title = getString(R.string.selection_title, state.selectedItems.size)
                subscriptionAdapter.submitList(state.filteredSubscriptions.toList())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiEvent.collect { event ->
                when (event) {
                    is MainUiEvent.ShowUndo -> showUndoSnackbar(event.message)
                    is MainUiEvent.ShowError -> showSnackbar(event.message, isError = true)
                    is MainUiEvent.ShowMessage -> showSnackbar(event.message)
                    is MainUiEvent.OpenUrl -> openUrlInCustomTab(event.url)
                }
            }
        }
    }

    // YENİ FONKSİYON
    private fun checkEmptyState(subscriptions: List<Subscription>) {
        if (subscriptions.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.subscriptionsRecyclerView.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.subscriptionsRecyclerView.visibility = View.VISIBLE
        }
    }

    // GÜNCELLENMİŞ Snackbar FONKSİYONU
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
            Log.e("SubscriptionListFragment", "Custom Tab açılamadı, standart intent kullanılıyor.", e)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
