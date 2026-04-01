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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.data.UnsubscribeAction
import com.tarik.mailcleanup.databinding.FragmentSubscriptionListBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SubscriptionListFragment : Fragment() {

    private var _binding: FragmentSubscriptionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var subscriptionAdapter: SubscriptionAdapter
    private var currentAccount: GoogleSignInAccount? = null
    
    // --- YENİ: Listenin sonuna gelinip gelinmediğini takip edecek bayrak ---
    private var isLastPage = false
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
                if (viewModel.isSelectionMode.value == true) {
                    viewModel.toggleSelection(subscription)
                }
            },
            longClickListener = { subscription ->
                if (viewModel.isSelectionMode.value != true) {
                    viewModel.startSelectionMode(subscription)
                }
                true
            },
            isSelectionMode = { viewModel.isSelectionMode.value == true },
            isSelected = { subscription ->
                viewModel.selectedItems.value?.contains(subscription) == true
            },
            onUnsubscribeClicked = { subscription ->
                showUnsubscribeConfirmationDialog(subscription)
            },
            onKeepClicked = { subscription ->
                viewModel.keepSubscription(subscription)
            }
        )
        binding.subscriptionsRecyclerView.adapter = subscriptionAdapter
        binding.subscriptionsRecyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        
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
            viewModel.scanState.collect { state ->
                if (state is ScanState.Success) {
                    subscriptionAdapter.submitList(state.subscriptions)
                    // YENİ: Boş durum kontrolü
                    checkEmptyState(state.subscriptions)
                } else if (state is ScanState.InProgress) {
                    // Tarama başlarken boş ekranı gizle
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.subscriptionsRecyclerView.visibility = View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unsubscribeState.collect { state ->
                when (state) {
                    is UnsubscribeState.InProgress -> subscriptionAdapter.setProcessingState(state.email)
                    is UnsubscribeState.Success -> {
                        subscriptionAdapter.setProcessingState(null)
                        val message = when (state.action) {
                            is UnsubscribeAction.MailTo -> getString(R.string.snackbar_unsubscribed_mailto, state.email)
                            else -> getString(R.string.snackbar_unsubscribed_success)
                        }
                        showUndoSnackbar(message)
                        if (state.action is UnsubscribeAction.Http) {
                            openUrlInCustomTab(state.action.url)
                        }
                    }
                    is UnsubscribeState.Error -> {
                        subscriptionAdapter.setProcessingState(null)
                        showSnackbar("'${state.email}' için hata: ${state.message}", isError = true)
                    }
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.keepState.collect { state ->
                 when (state) {
                    is KeepState.Success -> showUndoSnackbar(getString(R.string.snackbar_kept, state.email))
                    is KeepState.Error -> showSnackbar("'${state.email}' için hata: ${state.message}", isError = true)
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadMoreState.collect { state ->
                // Gelen her durumu logla
                Log.d("FragmentDebug", "Yeni loadMoreState alındı: ${state::class.simpleName}")

                when (state) {
                    is LoadMoreState.InProgress -> {
                        Log.d("FragmentDebug", "ProgressBar GÖSTERİLİYOR")
                        binding.loadMoreProgressBar.visibility = View.VISIBLE
                    }
                    is LoadMoreState.NoMoreData -> {
                        Log.d("FragmentDebug", "ProgressBar GİZLENİYOR")
                        binding.loadMoreProgressBar.visibility = View.GONE
                        Log.d("FragmentDebug", "isLastPage = true olarak ayarlandı.")
                        isLastPage = true
                        showSnackbar(getString(R.string.snackbar_all_loaded))
                    }
                    is LoadMoreState.Error -> {
                        Log.d("FragmentDebug", "ProgressBar GİZLENİYOR")
                        binding.loadMoreProgressBar.visibility = View.GONE
                        showSnackbar(state.message, isError = true)
                    }
                    is LoadMoreState.Success, is LoadMoreState.Idle -> {
                        Log.d("FragmentDebug", "ProgressBar GİZLENİYOR")
                        binding.loadMoreProgressBar.visibility = View.GONE
                    }
                }
            }
        }
        
        // YENİ: SEÇİM DURUMU GÖZLEMLERİ
        viewModel.isSelectionMode.observe(viewLifecycleOwner) { inSelectionMode ->
            if (inSelectionMode && actionMode == null) {
                actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
            } else if (!inSelectionMode && actionMode != null) {
                // ViewModel seçim modunun bittiğini söylediğinde, modu kapat.
                actionMode?.finish()
            }
        }

        viewModel.selectedItems.observe(viewLifecycleOwner) { selection ->
            actionMode?.title = getString(R.string.selection_title, selection.size)
            // ListAdapter kullandığımız için, seçim değiştiğinde tüm listeyi
            // yeniden göndermek en basit yöntemdir.
            subscriptionAdapter.submitList(subscriptionAdapter.currentList.toList())
        }

        // YENİ: Toplu işlem sonucunu dinle
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.bulkActionResult.collectLatest { message ->
                showSnackbar(message)
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