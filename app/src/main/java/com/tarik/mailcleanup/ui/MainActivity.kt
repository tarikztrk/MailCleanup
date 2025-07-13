package com.tarik.mailcleanup.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                handleSignInSuccess(account)
            } catch (e: ApiException) {
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

    override fun onStart() {
        super.onStart()
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY))) {
            handleSignInSuccess(account)
        } else {
            viewModel.resetToIdleState()
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(GmailScopes.GMAIL_READONLY))
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

    private fun handleSignInSuccess(account: GoogleSignInAccount) {
        viewModel.onSignInSuccess(account.displayName)
        viewModel.startSubscriptionScan(account)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.scanState.collectLatest { state ->
                when (state) {
                    is ScanState.InProgress -> showLoadingView("Abonelikleriniz taranıyor...")
                    is ScanState.Success -> showSubscriptionList()
                    is ScanState.Error -> showSignInView(state.message)
                    is ScanState.Idle -> { /* SignInState tarafından yönetilecek */ }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.signInState.collectLatest { state ->
                when (state) {
                    is SignInState.InProgress -> showLoadingView("Giriş yapılıyor...")
                    is SignInState.Idle -> showSignInView(null)
                    is SignInState.Error -> showSignInView(state.message)
                    is SignInState.Success -> { /* ScanState'e devredildi */ }
                }
            }
        }
    }

    private fun showLoadingView(message: String) {
        removeFragment()
        binding.signInLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.signInButton.visibility = View.GONE
        binding.statusTextView.text = message
    }

    private fun showSignInView(errorMessage: String?) {
        removeFragment()
        binding.signInLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.signInButton.visibility = View.VISIBLE
        binding.statusTextView.text = errorMessage ?: "Giriş yapmak için butona tıklayın."
    }

    private fun showSubscriptionList() {
        binding.signInLayout.visibility = View.GONE
        if (supportFragmentManager.findFragmentByTag("SubscriptionListFragment") == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SubscriptionListFragment::class.java, null, "SubscriptionListFragment")
                .commit()
        }
    }

    private fun removeFragment() {
        supportFragmentManager.findFragmentByTag("SubscriptionListFragment")?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
    }
}