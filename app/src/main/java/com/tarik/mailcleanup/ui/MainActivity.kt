package com.tarik.mailcleanup.ui
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.tarik.mailcleanup.databinding.ActivityMainBinding

class hganhangiMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels() // ViewModel'i tembel başlatma (lazy init)

    private lateinit var googleSignInClient: GoogleSignInClient

    // Modern yöntem: Google giriş ekranından gelen sonucu yakalamak için.
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("DEBUG", "Yeni giriş başarılı, handleSignInSuccess çağrılıyor.")
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                // BURASI KRİTİK: Bu fonksiyon çağrılmalı.
                handleSignInSuccess(account)
            } catch (e: ApiException) {
                Log.e("MainActivity", "Giriş hatası: ${e.statusCode}", e)
                viewModel.onSignInFailed("Giriş başarısız oldu. Kod: ${e.statusCode}")
            }
        } else {
            viewModel.onSignInFailed("Giriş işlemi kullanıcı tarafından iptal edildi.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureGoogleSignIn()
        setupClickListeners()
        observeViewModel()
    }

    private fun configureGoogleSignIn() {
        // Google Giriş Seçeneklerini Yapılandırma
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // Gmail API'sini kullanmak için İZİN İSTİYORUZ. Bu en önemli satır!
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
            // Kullanıcının e-posta adresini istiyoruz.
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupClickListeners() {
        binding.signInButton.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        viewModel.onSignInStarted()
        val signInIntent: Intent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    // ViewModel'deki değişiklikleri dinleyip UI'ı güncelleme
    private fun observeViewModel() {
        viewModel.signInState.observe(this) { state ->
            when (state) {
                is SignInState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.signInButton.visibility = View.VISIBLE
                    binding.statusTextView.text = "Giriş yapmak için butona tıklayın."
                }
                is SignInState.InProgress -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.signInButton.visibility = View.GONE
                    binding.statusTextView.text = "Giriş yapılıyor..."
                }
                is SignInState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.signInButton.visibility = View.GONE
                    binding.statusTextView.text = "Hoş geldin, ${state.displayName}!"
                    // TODO: Başarılı giriş sonrası bir sonraki ekrana geçiş kodu burada olacak.
                }
                is SignInState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.signInButton.visibility = View.VISIBLE
                    binding.statusTextView.text = "Hata: ${state.message}"
                }
            }
        }

        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanState.InProgress -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.statusTextView.text = "Abonelikleriniz taranıyor..."
                }
                is ScanState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val count = state.subscriptions.size // Değişiklik
                    binding.statusTextView.text = "Harika! $count adet farklı göndericiden abonelik bulundu."
                    // TODO: Buradan yeni bir Fragment/Activity'e geçip listeyi göstereceğiz.
                }
                is ScanState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.statusTextView.text = "Hata: ${state.message}"
                }
                is ScanState.Idle -> {
                    // Bir şey yapmaya gerek yok
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("DEBUG", "Mevcut giriş bulundu, handleSignInSuccess çağrılıyor.")
        val account = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null && GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY))) {
            handleSignInSuccess(account)
        } else {
            // DOĞRU YÖNTEM: ViewModel'e durumu sıfırlamasını söyle.
            viewModel.resetToIdleState()
        }
    }

    private fun handleSignInSuccess(account: GoogleSignInAccount) {
        Log.d("DEBUG", "handleSignInSuccess içinde, startSubscriptionScan çağrılıyor.")
        Log.d("MainActivity", "Giriş başarılı, tarama başlıyor: ${account.email}")
        viewModel.onSignInSuccess(account.displayName)

        // BURASI EN KRİTİK NOKTA: Bu satır kesinlikle olmalı.
        viewModel.startSubscriptionScan(account)
    }
}