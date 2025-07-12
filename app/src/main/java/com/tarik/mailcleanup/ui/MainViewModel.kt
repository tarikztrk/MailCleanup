package com.tarik.mailcleanup.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.data.EmailRepository
import kotlinx.coroutines.launch
import com.google.api.services.gmail.model.Message
import com.tarik.mailcleanup.data.Subscription

sealed class ScanState {
    object Idle : ScanState()
    object InProgress : ScanState()
    data class Success(val subscriptions: List<Subscription>) : ScanState() // Değişiklik
    data class Error(val message: String) : ScanState()
}

// Giriş işleminin durumlarını temsil edecek bir mühürlü sınıf (sealed class)
sealed class SignInState {
    object Idle : SignInState() // Başlangıç durumu
    object InProgress : SignInState() // Giriş işlemi sürüyor
    data class Success(val displayName: String) : SignInState() // Başarılı giriş
    data class Error(val message: String) : SignInState() // Hata durumu
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // UI'ın gözlemleyeceği (observe) LiveData
    private val _signInState = MutableLiveData<SignInState>(SignInState.Idle)
    val signInState: LiveData<SignInState> = _signInState

    // Giriş işlemi başladığında bu fonksiyon çağrılacak
    fun onSignInStarted() {
        _signInState.value = SignInState.InProgress
    }

    // Giriş işlemi başarılı olduğunda bu fonksiyon çağrılacak
    fun onSignInSuccess(displayName: String?) {
        _signInState.value = SignInState.Success(displayName ?: "Kullanıcı")
    }

    // Giriş işlemi başarısız olduğunda bu fonksiyon çağrılacak
    fun onSignInFailed(errorMessage: String?) {
        _signInState.value = SignInState.Error(errorMessage ?: "Bilinmeyen bir hata oluştu.")
    }

    private val repository = EmailRepository(application.applicationContext)
    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    fun startSubscriptionScan(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _scanState.value = ScanState.InProgress
            try {
                val subscriptions = repository.getSubscriptions(account) // Yeni fonksiyonu çağır
                _scanState.value = ScanState.Success(subscriptions)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Abonelikler taranırken bir hata oluştu.")
            }
        }
    }

    fun resetToIdleState() {
        // ViewModel, kendi özel (_signInState) verisini kendisi değiştirir.
        _signInState.value = SignInState.Idle
        _scanState.value = ScanState.Idle
    }
}