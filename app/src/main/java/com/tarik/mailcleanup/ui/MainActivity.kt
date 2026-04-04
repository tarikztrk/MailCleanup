package com.tarik.mailcleanup.ui

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.gmail.GmailScopes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.ActivityMainBinding
import com.tarik.mailcleanup.domain.model.MailAccount
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var credentialManager: CredentialManager
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }

    private var pendingMailAccount: MailAccount? = null

    private val authorizationResolutionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pendingAccount = pendingMailAccount
        pendingMailAccount = null

        if (result.resultCode != RESULT_OK || pendingAccount == null) {
            viewModel.onSignInFailed(getString(R.string.error_sign_in_cancelled))
            return@registerForActivityResult
        }

        val intent = result.data
        if (intent == null) {
            viewModel.onSignInFailed(getString(R.string.error_sign_in_cancelled))
            return@registerForActivityResult
        }

        try {
            val authorizationResult = authorizationClient.getAuthorizationResultFromIntent(intent)
            if (hasGmailModifyScope(authorizationResult)) {
                handleSignInSuccess(pendingAccount)
            } else {
                viewModel.onSignInFailed(getString(R.string.error_auth))
            }
        } catch (e: ApiException) {
            viewModel.onSignInFailed(getString(R.string.error_sign_in_failed, e.statusCode.toString()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        credentialManager = CredentialManager.create(this)
        setupClickListeners()
        showSignInView(null)
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        val cached = loadCachedMailAccount()
        if (cached != null) {
            requestGmailAuthorization(cached, silentOnly = true)
        } else {
            viewModel.resetToIdleState()
        }
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
        lifecycleScope.launch {
            runCatching {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(
                        GetGoogleIdOption.Builder()
                            .setServerClientId(getString(R.string.google_server_client_id))
                            .setFilterByAuthorizedAccounts(false)
                            .setAutoSelectEnabled(false)
                            .build()
                    )
                    .build()

                credentialManager.getCredential(this@MainActivity, request)
            }.onSuccess { response ->
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val accountName = googleIdTokenCredential.id
                    val email = googleIdTokenCredential.id
                    val mailAccount = MailAccount(accountName = accountName, email = email)
                    requestGmailAuthorization(mailAccount, silentOnly = false)
                } else {
                    viewModel.onSignInFailed(getString(R.string.error_sign_in_failed, "unsupported_credential"))
                }
            }.onFailure { throwable ->
                val message = if (throwable is GetCredentialException) {
                    throwable.message ?: getString(R.string.error_sign_in_cancelled)
                } else {
                    getString(R.string.error_sign_in_failed, "credential_error")
                }
                viewModel.onSignInFailed(message)
            }
        }
    }

    private fun requestGmailAuthorization(account: MailAccount, silentOnly: Boolean) {
        val request = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(GmailScopes.GMAIL_MODIFY)))
            .setAccount(Account(account.accountName, "com.google"))
            .build()

        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                handleAuthorizationResult(account, result, silentOnly)
            }
            .addOnFailureListener {
                if (silentOnly) {
                    viewModel.resetToIdleState()
                } else {
                    viewModel.onSignInFailed(getString(R.string.error_auth))
                }
            }
    }

    private fun handleAuthorizationResult(
        account: MailAccount,
        result: AuthorizationResult,
        silentOnly: Boolean
    ) {
        when {
            result.hasResolution() && !silentOnly -> {
                pendingMailAccount = account
                val pendingIntent = result.pendingIntent
                if (pendingIntent == null) {
                    viewModel.onSignInFailed(getString(R.string.error_auth))
                    return
                }
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                authorizationResolutionLauncher.launch(request)
            }
            hasGmailModifyScope(result) -> {
                handleSignInSuccess(account)
            }
            else -> {
                if (silentOnly) {
                    viewModel.resetToIdleState()
                } else {
                    viewModel.onSignInFailed(getString(R.string.error_auth))
                }
            }
        }
    }

    private fun handleSignInSuccess(account: MailAccount) {
        cacheMailAccount(account)
        viewModel.onSignInSuccess()
        viewModel.startSubscriptionScan(account)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
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
        }
    }

    private fun showLoadingView(message: String) {
        removeFragment()
        binding.appBarLayout.visibility = View.GONE
        binding.signInLayout.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
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
        binding.fragmentContainer.visibility = View.GONE
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
        binding.fragmentContainer.visibility = View.VISIBLE
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

    private fun cacheMailAccount(account: MailAccount) {
        getSharedPreferences("mail_cleanup_auth", MODE_PRIVATE)
            .edit()
            .putString("account_name", account.accountName)
            .putString("account_email", account.email)
            .apply()
    }

    private fun loadCachedMailAccount(): MailAccount? {
        val prefs = getSharedPreferences("mail_cleanup_auth", MODE_PRIVATE)
        val accountName = prefs.getString("account_name", null) ?: return null
        return MailAccount(
            accountName = accountName,
            email = prefs.getString("account_email", null)
        )
    }

    private fun hasGmailModifyScope(result: AuthorizationResult): Boolean {
        return result.grantedScopes.any { grantedScope ->
            grantedScope.toString() == GmailScopes.GMAIL_MODIFY
        }
    }
}
