package com.tarik.mailcleanup.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubscriptionListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentAccount = GoogleSignIn.getLastSignedInAccount(requireContext())
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        subscriptionAdapter = SubscriptionAdapter(
            onUnsubscribeClicked = { subscription ->
                // Artık doğrudan ViewModel'i çağırmıyoruz, önce diyalog gösteriyoruz.
                showUnsubscribeConfirmationDialog(subscription)
            },
            onKeepClicked = { subscription ->
                // Keep butonuna tıklandığında
                showKeepConfirmationDialog(subscription)
            }
        )
        binding.subscriptionsRecyclerView.adapter = subscriptionAdapter
        binding.subscriptionsRecyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
    }
    
    // --- YENİ FONKSİYON: Onay Diyaloğu ---
    private fun showUnsubscribeConfirmationDialog(subscription: Subscription) {
        // Material Design kütüphanesinden modern bir diyalog kullanıyoruz.
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Abonelikten Çık ve Temizle?")
            .setMessage("'${subscription.senderName}' aboneliğinden çıkılacak. Ayrıca bu göndericiden gelen mevcut tüm e-postalar çöp kutusuna taşınsın mı?")
            .setNegativeButton("Sadece Çık") { dialog, which ->
                // Kullanıcı sadece çıkmak istedi. "Temizle" parametresini false gönder.
                currentAccount?.let { account ->
                    viewModel.unsubscribeAndClean(account, subscription, cleanEmails = false)
                }
            }
            .setPositiveButton("Evet, Çık ve Temizle") { dialog, which ->
                // Kullanıcı hem çıkmak hem de temizlemek istedi. "Temizle" parametresini true gönder.
                currentAccount?.let { account ->
                    viewModel.unsubscribeAndClean(account, subscription, cleanEmails = true)
                }
            }
            .setNeutralButton("İptal", null) // "İptal" butonu hiçbir şey yapmaz, diyalog kapanır.
            .show()
    }

    // --- YENİ FONKSİYON: Keep Onay Diyaloğu ---
    private fun showKeepConfirmationDialog(subscription: Subscription) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Aboneliği Koru?")
            .setMessage("'${subscription.senderName}' aboneliği beyaz listeye eklenecek ve bir sonraki taramada gösterilmeyecek.")
            .setPositiveButton("Evet, Koru") { dialog, which ->
                viewModel.keepSubscription(subscription)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                if (state is ScanState.Success) {
                    // YENİ GÜNCELLEME YÖNTEMİ: submitList()
                    subscriptionAdapter.submitList(state.subscriptions)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unsubscribeState.collectLatest { state ->
                when (state) {
                    is UnsubscribeState.InProgress -> {
                        // Adapter'a hangi öğenin işlendiğini söyle
                        subscriptionAdapter.setProcessingState(state.email)
                    }
                    is UnsubscribeState.Success -> {
                        subscriptionAdapter.setProcessingState(null) // İşlem bitince yükleniyor durumunu kaldır
                        // İşlem başarılı olduğunda, o öğeyi listeden çıkaralım
                        val currentList = subscriptionAdapter.currentList.toMutableList()
                        val newList = currentList.filterNot { it.senderEmail == state.email }
                        subscriptionAdapter.submitList(newList)
                        
                        val message = when (state.action) {
                            is UnsubscribeAction.MailTo -> "${state.email} için çıkış e-postası gönderildi."
                            is UnsubscribeAction.Http -> "Çıkış sayfası açılıyor..."
                            else -> "İşlem başarılı."
                        }
                        showSnackbar(message)

                        if(state.action is UnsubscribeAction.Http) {
                           openUrlInCustomTab(state.action.url)
                        }
                    }
                    is UnsubscribeState.Error -> {
                        subscriptionAdapter.setProcessingState(null) // Hata durumunda da yükleniyor durumunu kaldır
                        showSnackbar("${state.email} için hata: ${state.message}", isError = true)
                    }
                    is UnsubscribeState.Idle -> { }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.keepState.collectLatest { state ->
                when (state) {
                    is KeepState.InProgress -> {
                        // Adapter'a hangi öğenin işlendiğini söyle
                        subscriptionAdapter.setProcessingState(state.email)
                    }
                    is KeepState.Success -> {
                        subscriptionAdapter.setProcessingState(null) // İşlem bitince yükleniyor durumunu kaldır
                        // İşlem başarılı olduğunda, o öğeyi listeden çıkaralım
                        val currentList = subscriptionAdapter.currentList.toMutableList()
                        val newList = currentList.filterNot { it.senderEmail == state.email }
                        subscriptionAdapter.submitList(newList)
                        
                        // "Geri Al" seçeneği olan bir Snackbar göster
                        showUndoSnackbar("'${state.email}' korunanlara eklendi.")
                    }
                    is KeepState.Error -> {
                        subscriptionAdapter.setProcessingState(null) // Hata durumunda da yükleniyor durumunu kaldır
                        showSnackbar("${state.email} için hata: ${state.message}", isError = true)
                    }
                    is KeepState.Idle -> { }
                }
            }
        }
    }

    // --- YENİ YARDIMCI FONKSİYON: Snackbar Gösterme ---
    private fun showSnackbar(message: String, isError: Boolean = false) {
        // Fragment'ın view'ı null değilse devam et
        view?.let {
            val snackbar = Snackbar.make(it, message, Snackbar.LENGTH_LONG)
            if (isError) {
                snackbar.setBackgroundTint(resources.getColor(com.google.android.material.R.color.design_default_color_error, null))
            }
            snackbar.show()
        }
    }

    // YENİ FONKSİYON: Geri Alma Seçenekli Snackbar
    private fun showUndoSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setAction("Geri Al") {
                    // Kullanıcı "Geri Al" butonuna bastı
                    viewModel.undoKeepAction()
                }
                .addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        // Eğer Snackbar "Geri Al"a basılmadan kaybolursa (timeout, swipe),
                        // işlemi kalıcı hale getir.
                        if (event != DISMISS_EVENT_ACTION) {
                            viewModel.finalizeKeepAction()
                        }
                    }
                })
                .show()
        }
    }

    // YENİ YARDIMCI FONKSİYON
    private fun openUrlInCustomTab(url: String) {
        try {
            val builder = CustomTabsIntent.Builder()
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
        } catch (e: Exception) {
            // Cihazda Custom Tab destekleyen bir tarayıcı yoksa
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