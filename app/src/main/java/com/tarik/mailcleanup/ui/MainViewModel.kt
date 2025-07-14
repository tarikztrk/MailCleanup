package com.tarik.mailcleanup.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.data.EmailRepository
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.data.UnsubscribeAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class SignInState {
    object Idle : SignInState()
    object InProgress : SignInState()
    data class Success(val displayName: String) : SignInState()
    data class Error(val message: String) : SignInState()
}

sealed class ScanState {
    object Idle : ScanState()
    object InProgress : ScanState()
    data class Success(val subscriptions: List<Subscription>) : ScanState()
    data class Error(val message: String) : ScanState()
}

sealed class UnsubscribeState {
    object Idle : UnsubscribeState()
    data class InProgress(val email: String) : UnsubscribeState()
    data class Success(val email: String, val action: UnsubscribeAction) : UnsubscribeState() // Artık aksiyonu da taşıyor
    data class Error(val email: String, val message: String) : UnsubscribeState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmailRepository(application.applicationContext)

    private val _signInState = MutableSharedFlow<SignInState>(replay = 1)
    val signInState = _signInState.asSharedFlow()

    private val _scanState = MutableSharedFlow<ScanState>(replay = 1)
    val scanState = _scanState.asSharedFlow()

    private val _unsubscribeState = MutableSharedFlow<UnsubscribeState>()
    val unsubscribeState = _unsubscribeState.asSharedFlow()

    init {
        resetToIdleState()
    }

    fun onSignInStarted() {
        _signInState.tryEmit(SignInState.InProgress)
    }

    fun onSignInSuccess(displayName: String?) {
        _signInState.tryEmit(SignInState.Success(displayName ?: "Kullanıcı"))
    }

    fun onSignInFailed(errorMessage: String?) {
        _signInState.tryEmit(SignInState.Error(errorMessage ?: "Bilinmeyen bir hata oluştu."))
    }

    fun startSubscriptionScan(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _scanState.tryEmit(ScanState.InProgress)
            try {
                val subscriptions = repository.getSubscriptions(account)
                _scanState.tryEmit(ScanState.Success(subscriptions))
            } catch (e: Exception) {
                _scanState.tryEmit(ScanState.Error("Abonelikler taranırken bir hata oluştu."))
            }
        }
    }

    fun resetToIdleState() {
        _signInState.tryEmit(SignInState.Idle)
        _scanState.tryEmit(ScanState.Idle)
    }

    fun unsubscribeAndClean(
        account: GoogleSignInAccount,
        subscription: Subscription,
        cleanEmails: Boolean // Yeni parametre
    ) {
        viewModelScope.launch {
            _unsubscribeState.emit(UnsubscribeState.InProgress(subscription.senderEmail))
            // Repository'deki yeni fonksiyonu çağırıyoruz.
            val action = repository.unsubscribeAndClean(account, subscription, cleanEmails)
            
            if (action !is UnsubscribeAction.NotFound) {
                _unsubscribeState.emit(UnsubscribeState.Success(subscription.senderEmail, action))
                // Listeyi güncelleme mantığı aynı
                val currentState = scanState.replayCache.firstOrNull()
                if (currentState is ScanState.Success) {
                    val newList = currentState.subscriptions.filterNot { it.senderEmail == subscription.senderEmail }
                    _scanState.emit(ScanState.Success(newList))
                }
            } else {
                _unsubscribeState.emit(UnsubscribeState.Error(subscription.senderEmail, "Abonelikten çıkma yöntemi bulunamadı."))
            }
        }
    }
}