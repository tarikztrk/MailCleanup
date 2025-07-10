package com.tarik.mailcleanup.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.data.EmailRepository
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState() // Başlangıç durumu
    object InProgress : ScanState() // Tarama sürüyor
    data class Success(val count: Int) : ScanState() // Başarılı tarama
    data class Error(val message: String) : ScanState() // Hata durumu
}

// Giriş işleminin durumlarını temsil edecek bir mühürlü sınıf (sealed class)
sealed class SignInState {
    object Idle : SignInState() // Başlangıç durumu
    object InProgress : SignInState() // Giriş işlemi sürüyor
    data class Success(val displayName: String) : SignInState() // Başarılı giriş
    data class Error(val message: String) : SignInState() // Hata durumu
}

class MainViewModel : ViewModel() {

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

    private val repository = EmailRepository() // Şimdilik doğrudan oluşturuyoruz.

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    fun startSubscriptionScan(context: Context, account: GoogleSignInAccount) {
        // ViewModelScope, bu coroutine'in ViewModel yaşam döngüsüne bağlı olmasını sağlar.
        viewModelScope.launch {
            _scanState.value = ScanState.InProgress
            try {
                val count = repository.findSubscriptionEmails(context, account)
                _scanState.value = ScanState.Success(count)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Abonelikler taranırken bir hata oluştu.")
            }
        }
    }
}