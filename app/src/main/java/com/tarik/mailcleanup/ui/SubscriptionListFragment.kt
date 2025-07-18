package com.tarik.mailcleanup.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SubscriptionListFragment : Fragment() {

    private var _binding: FragmentSubscriptionListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var subscriptionAdapter: SubscriptionAdapter
    private var currentAccount: GoogleSignInAccount? = null
    private var actionMode: ActionMode? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSubscriptionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        setupRecyclerView()
        observeViewModel()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            return true
        }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.action_unsubscribe -> {
                    showBulkUnsubscribeConfirmationDialog()
                    return true
                }
                R.id.action_keep -> {
                    viewModel.bulkKeep()
                    mode.finish()
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
            viewModel.clearSelection()
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

    private fun showUnsubscribeConfirmationDialog(subscription: Subscription) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Abonelikten Çık ve Temizle?")
            .setMessage("'${subscription.senderName}' aboneliğinden çıkılacak. Ayrıca bu göndericiden gelen mevcut tüm e-postalar çöp kutusuna taşınsın mı?")
            .setNegativeButton("Sadece Çık") { _, _ ->
                currentAccount?.let { viewModel.unsubscribeAndClean(it, subscription, cleanEmails = false) }
            }
            .setPositiveButton("Evet, Çık ve Temizle") { _, _ ->
                currentAccount?.let { viewModel.unsubscribeAndClean(it, subscription, cleanEmails = true) }
            }
            .setNeutralButton("İptal", null)
            .show()
    }

    private fun showBulkUnsubscribeConfirmationDialog() {
        val selectedCount = viewModel.getSelectedItemsCount()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Toplu Abonelikten Çıkış")
            .setMessage("$selectedCount abonelikten çıkılacak. Ayrıca bu gönderücilerden gelen mevcut tüm e-postalar da çöp kutusuna taşınsın mı?")
            .setNegativeButton("Sadece Çık") { _, _ ->
                currentAccount?.let { viewModel.bulkUnsubscribe(it, cleanEmails = false) }
                actionMode?.finish()
            }
            .setPositiveButton("Evet, Çık ve Temizle") { _, _ ->
                currentAccount?.let { viewModel.bulkUnsubscribe(it, cleanEmails = true) }
                actionMode?.finish()
            }
            .setNeutralButton("İptal", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
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
            viewModel.unsubscribeState.collectLatest { state ->
                when (state) {
                    is UnsubscribeState.InProgress -> subscriptionAdapter.setProcessingState(state.email)
                    is UnsubscribeState.Success -> {
                        subscriptionAdapter.setProcessingState(null)
                        val message = when (state.action) {
                            is UnsubscribeAction.MailTo -> "'${state.email}' için çıkış e-postası gönderildi."
                            else -> "İşlem başarılı."
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
            viewModel.keepState.collectLatest { state ->
                 when (state) {
                    is KeepState.Success -> showUndoSnackbar("'${state.email}' korunanlara eklendi.")
                    is KeepState.Error -> showSnackbar("'${state.email}' korunurken bir hata oluştu.", isError = true)
                    else -> {}
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadMoreState.collectLatest { state ->
                when (state) {
                    is LoadMoreState.InProgress -> {
                        binding.loadMoreProgressBar.visibility = View.VISIBLE
                    }
                    is LoadMoreState.Success, is LoadMoreState.NoMoreData, is LoadMoreState.Error -> {
                        binding.loadMoreProgressBar.visibility = View.GONE
                    }
                    else -> {
                        binding.loadMoreProgressBar.visibility = View.GONE
                    }
                }

                if (state is LoadMoreState.Error) {
                    showSnackbar(state.message, isError = true)
                }
                if (state is LoadMoreState.NoMoreData) {
                    showSnackbar("Tüm abonelikler yüklendi.", isError = false)
                }
            }
        }
        
        // YENİ: SEÇİM DURUMU GÖZLEMLERİ
        viewModel.isSelectionMode.observe(viewLifecycleOwner) { inSelectionMode ->
            if (inSelectionMode && actionMode == null) {
                actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
            } else if (!inSelectionMode && actionMode != null) {
                actionMode?.finish()
            }
        }

        viewModel.selectedItems.observe(viewLifecycleOwner) { selection ->
            actionMode?.title = "${selection.size} öğe seçildi"
            subscriptionAdapter.notifyDataSetChanged() // Seçim değiştiğinde listeyi yenile
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
                .setAction("Geri Al") {
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