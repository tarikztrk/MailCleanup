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
                viewModel.onSignInFailed(getString(R.string.error_sign_in_failed, e.statusCode.toString()))
            }
        } else {
            viewModel.onSignInFailed(getString(R.string.error_sign_in_cancelled))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // YENİ: Toolbar'ı ayarla
        setSupportActionBar(binding.toolbar)

        configureGoogleSignIn()
        setupClickListeners()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_MODIFY))) {
            handleSignInSuccess(account)
        } else {
            viewModel.resetToIdleState()
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // İZNİ GMAIL_MODIFY OLARAK GÜNCELLİYORUZ
            // Bu, okuma, oluşturma ve gönderme yetkisi verir.
            .requestScopes(Scope(GmailScopes.GMAIL_MODIFY))
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
                    is ScanState.InProgress -> showLoadingView(getString(R.string.status_scanning))
                    is ScanState.Success -> showSubscriptionList()
                    is ScanState.Error -> showSignInView(state.message)
                    is ScanState.Idle -> { /* SignInState tarafından yönetilecek */ }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.signInState.collectLatest { state ->
                when (state) {
                    is SignInState.InProgress -> showLoadingView(getString(R.string.status_signing_in))
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
        binding.statusTextView.text = errorMessage ?: getString(R.string.status_initial)
    }

    private fun showSubscriptionList() {
        binding.signInLayout.visibility = View.GONE
        if (supportFragmentManager.findFragmentByTag(getString(R.string.fragment_tag_subscription_list)) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SubscriptionListFragment::class.java, null, getString(R.string.fragment_tag_subscription_list))
                .commit()
        }
    }

    private fun removeFragment() {
        supportFragmentManager.findFragmentByTag(getString(R.string.fragment_tag_subscription_list))?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
    }
}