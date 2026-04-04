package com.tarik.mailcleanup.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                handleSignInSuccess(account)
            } else {
                viewModel.onSignInFailed(getString(R.string.error_sign_in_cancelled))
            }
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED) {
                viewModel.onSignInFailed(getString(R.string.error_sign_in_cancelled))
            } else {
                viewModel.onSignInFailed(getString(R.string.error_sign_in_failed, e.statusCode.toString()))
            }
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
        binding.outlookButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.error_provider_not_supported), Toast.LENGTH_SHORT).show()
        }
        binding.imapTextButton.setOnClickListener {
            Toast.makeText(this, getString(R.string.error_provider_not_supported), Toast.LENGTH_SHORT).show()
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
            viewModel.uiState.collect { state ->
                when {
                    state.scanStatus is ScanUiStatus.InProgress -> showLoadingView(getString(R.string.status_scanning))
                    state.scanStatus is ScanUiStatus.Success -> showSubscriptionList()
                    state.scanStatus is ScanUiStatus.Error -> showSignInView((state.scanStatus as ScanUiStatus.Error).message)
                    state.signInStatus is SignInUiStatus.InProgress -> showLoadingView(getString(R.string.status_signing_in))
                    state.signInStatus is SignInUiStatus.Error -> showSignInView((state.signInStatus as SignInUiStatus.Error).message)
                    else -> showSignInView(null)
                }
            }
        }
    }

    private fun showLoadingView(message: String) {
        removeFragment()
        binding.appBarLayout.visibility = View.GONE
        binding.signInLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.signInButton.visibility = View.GONE
        binding.outlookButton.visibility = View.GONE
        binding.imapTextButton.visibility = View.GONE
        binding.statusTextView.visibility = View.VISIBLE
        binding.statusTextView.text = message
    }

    private fun showSignInView(errorMessage: String?) {
        removeFragment()
        binding.appBarLayout.visibility = View.GONE
        binding.signInLayout.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.signInButton.visibility = View.VISIBLE
        binding.outlookButton.visibility = View.VISIBLE
        binding.imapTextButton.visibility = View.VISIBLE
        if (errorMessage.isNullOrBlank()) {
            binding.statusTextView.visibility = View.GONE
        } else {
            binding.statusTextView.visibility = View.VISIBLE
            binding.statusTextView.text = errorMessage
        }
    }

    private fun showSubscriptionList() {
        binding.appBarLayout.visibility = View.GONE
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
