package com.tarik.mailcleanup.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

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
}