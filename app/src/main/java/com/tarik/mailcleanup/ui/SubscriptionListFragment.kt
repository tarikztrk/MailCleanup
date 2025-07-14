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
        subscriptionAdapter = SubscriptionAdapter(emptyList()) { subscription ->
            // Artık doğrudan ViewModel'i çağırmıyoruz, önce diyalog gösteriyoruz.
            showUnsubscribeConfirmationDialog(subscription)
        }
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                if (state is ScanState.Success) {
                    subscriptionAdapter.updateData(state.subscriptions)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unsubscribeState.collectLatest { state ->
                when (state) {
                    is UnsubscribeState.Success -> {
                        // Aksiyonun türüne göre davran
                        when (state.action) {
                            is UnsubscribeAction.MailTo -> {
                                Toast.makeText(context, "${state.email} için çıkış e-postası gönderildi.", Toast.LENGTH_SHORT).show()
                            }
                            is UnsubscribeAction.Http -> {
                                Toast.makeText(context, "${state.email} için çıkış sayfası açılıyor...", Toast.LENGTH_SHORT).show()
                                openUrlInCustomTab(state.action.url)
                            }
                            is UnsubscribeAction.NotFound -> {
                                // Bu durum normalde Error state'ine düşer ama garanti olsun.
                                Toast.makeText(context, "${state.email} için çıkış yöntemi bulunamadı.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    is UnsubscribeState.Error -> {
                        Toast.makeText(context, "${state.email} için hata: ${state.message}", Toast.LENGTH_LONG).show()
                    }
                    is UnsubscribeState.InProgress, is UnsubscribeState.Idle -> {
                        // Şu an için bir şey yapmıyoruz.
                    }
                }
            }
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