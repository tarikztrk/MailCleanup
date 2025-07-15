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
    data class Success(val email: String, val action: UnsubscribeAction) : UnsubscribeState()
    data class Error(val email: String, val message: String) : UnsubscribeState()
}

sealed class KeepState {
    object Idle : KeepState()
    data class InProgress(val email: String) : KeepState()
    data class Success(val email: String) : KeepState()
    data class Error(val email: String, val message: String) : KeepState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmailRepository(application.applicationContext)
    private val _signInState = MutableSharedFlow<SignInState>(replay = 1)
    val signInState = _signInState.asSharedFlow()
    private val _scanState = MutableSharedFlow<ScanState>(replay = 1)
    val scanState = _scanState.asSharedFlow()
    private val _unsubscribeState = MutableSharedFlow<UnsubscribeState>()
    val unsubscribeState = _unsubscribeState.asSharedFlow()
    private val _keepState = MutableSharedFlow<KeepState>()
    val keepState = _keepState.asSharedFlow()

    // --- YENİ: Geri Alma Mantığı İçin ---
    private var lastAction: (() -> Unit)? = null

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

    fun unsubscribeAndClean(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean) {
        viewModelScope.launch {
            _unsubscribeState.emit(UnsubscribeState.InProgress(subscription.senderEmail))

            // Önce UI'ı güncelle
            val originalList = (scanState.replayCache.firstOrNull() as? ScanState.Success)?.subscriptions
            val newList = originalList?.filterNot { it.senderEmail == subscription.senderEmail }
            if (newList != null) {
                _scanState.emit(ScanState.Success(newList))
            }

            // Geri alma eylemini hazırla
            lastAction = {
                // Listeyi eski haline getir
                if (originalList != null) {
                    _scanState.tryEmit(ScanState.Success(originalList))
                }
            }

            // Arka planda gerçek işlemi yap
            val action = repository.unsubscribeAndClean(account, subscription, cleanEmails)
            
            if (action !is UnsubscribeAction.NotFound) {
                _unsubscribeState.emit(UnsubscribeState.Success(subscription.senderEmail, action))
            } else {
                _unsubscribeState.emit(UnsubscribeState.Error(subscription.senderEmail, "Abonelikten çıkma yöntemi bulunamadı."))
                // Hata durumunda listeyi geri yükle
                lastAction?.invoke() 
            }
        }
    }
    
    fun keepSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val originalList = (scanState.replayCache.firstOrNull() as? ScanState.Success)?.subscriptions
            val newList = originalList?.filterNot { it.senderEmail == subscription.senderEmail }
            if (newList != null) {
                _scanState.emit(ScanState.Success(newList))
            }

            lastAction = {
                if (originalList != null) {
                    _scanState.tryEmit(ScanState.Success(originalList))
                }
            }
            
            try {
                repository.keepSubscription(subscription)
                _keepState.emit(KeepState.Success(subscription.senderEmail))
            } catch (e: Exception) {
                _keepState.emit(KeepState.Error(subscription.senderEmail, "Hata oluştu"))
                lastAction?.invoke()
            }
        }
    }
    
    // YENİ FONKSİYON
    fun undoLastAction() {
        lastAction?.invoke()
        lastAction = null // Geri alma eylemi bir kez kullanılabilir.
    }

    // YENİ FONKSİYON
    fun finalizeLastAction() {
        // Bu fonksiyon, Snackbar kaybolduğunda çağrılır.
        // 'lastAction' veritabanı işlemini içerseydi, burada tetiklenirdi.
        // Mevcut yapıda, işlem hemen yapıldığı için bu fonksiyonu boş bırakabiliriz
        // veya sadece geçici veriyi temizleyebiliriz.
        lastAction = null
    }
}