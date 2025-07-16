package com.tarik.mailcleanup.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.data.EmailRepository
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.data.UnsubscribeAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Calendar

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

sealed class LoadMoreState {
    object Idle : LoadMoreState()
    object InProgress : LoadMoreState()
    object Success : LoadMoreState()
    object NoMoreData : LoadMoreState()
    data class Error(val message: String) : LoadMoreState()
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
    private val _loadMoreState = MutableSharedFlow<LoadMoreState>(replay = 1)
    val loadMoreState = _loadMoreState.asSharedFlow()

    // --- YENİ: MERKEZİ LİSTE YÖNETİMİ ---
    private val allSubscriptions = mutableListOf<Subscription>()
    private var lastAction: (() -> Unit)? = null
    private var lastEndDate: Calendar? = null
    private var isLoadingMore = false
    private var noMoreData = false

    init {
        resetToIdleState()
    }

    private fun updateScanState() {
        // Bu yardımcı fonksiyon, ana listeyi sıralayıp Flow'u günceller.
        val sortedList = allSubscriptions.sortedByDescending { it.emailCount }
        _scanState.tryEmit(ScanState.Success(sortedList))
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
        lastEndDate = null
        isLoadingMore = false
        noMoreData = false
        allSubscriptions.clear() // Taramayı sıfırlarken listeyi temizle
        _loadMoreState.tryEmit(LoadMoreState.Idle)
        
        viewModelScope.launch {
            _scanState.tryEmit(ScanState.InProgress)
            try {
                val (startDate, endDate) = getNextDateRange()
                val subscriptions = repository.getSubscriptions(account, startDate, endDate)
                allSubscriptions.addAll(subscriptions)
                updateScanState() // Merkezi fonksiyonu çağır
                lastEndDate = endDate
                Log.d("MainViewModel", "İlk tarama tamamlandı: ${subscriptions.size} abonelik bulundu")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Abonelik tarama hatası", e)
                _scanState.tryEmit(ScanState.Error("Abonelikler taranırken bir hata oluştu."))
            }
        }
    }

    fun loadMoreSubscriptions(account: GoogleSignInAccount) {
        if (isLoadingMore || noMoreData) return

        viewModelScope.launch {
            isLoadingMore = true
            _loadMoreState.tryEmit(LoadMoreState.InProgress)
            
            try {
                val (startDate, endDate) = getNextDateRange()
                val newSubscriptions = repository.getSubscriptions(account, startDate, endDate)
                
                if (newSubscriptions.isNotEmpty()) {
                    // Sadece gerçekten yeni olanları ekle
                    val currentEmails = allSubscriptions.map { it.senderEmail }.toSet()
                    val trulyNewItems = newSubscriptions.filter { !currentEmails.contains(it.senderEmail) }
                    
                    allSubscriptions.addAll(trulyNewItems)
                    updateScanState() // Ana listeyi güncelle ve yayınla
                    
                    lastEndDate = endDate
                    _loadMoreState.tryEmit(LoadMoreState.Success)
                    Log.d("MainViewModel", "Daha fazla yükleme tamamlandı: ${trulyNewItems.size} yeni abonelik eklendi.")
                } else {
                    noMoreData = true
                    _loadMoreState.tryEmit(LoadMoreState.NoMoreData)
                    Log.d("MainViewModel", "Daha fazla abonelik bulunamadı, tarama tamamlandı.")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Daha fazla yükleme hatası", e)
                _loadMoreState.tryEmit(LoadMoreState.Error("Daha fazla abonelik yüklenirken hata oluştu."))
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun unsubscribeAndClean(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean) {
        viewModelScope.launch {
            val originalIndex = allSubscriptions.indexOf(subscription)
            allSubscriptions.remove(subscription)
            updateScanState()

            lastAction = { allSubscriptions.add(originalIndex, subscription); updateScanState() }

            _unsubscribeState.emit(UnsubscribeState.InProgress(subscription.senderEmail))
            val action = repository.unsubscribeAndClean(account, subscription, cleanEmails)
            
            if (action is UnsubscribeAction.NotFound) {
                _unsubscribeState.emit(UnsubscribeState.Error(subscription.senderEmail, "Abonelikten çıkma yöntemi bulunamadı."))
                lastAction?.invoke()
            } else {
                _unsubscribeState.emit(UnsubscribeState.Success(subscription.senderEmail, action))
            }
        }
    }
    
    fun keepSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val originalIndex = allSubscriptions.indexOf(subscription)
            allSubscriptions.remove(subscription)
            updateScanState()

            lastAction = { allSubscriptions.add(originalIndex, subscription); updateScanState() }
            
            try {
                repository.keepSubscription(subscription)
                _keepState.emit(KeepState.Success(subscription.senderEmail))
            } catch (e: Exception) {
                _keepState.emit(KeepState.Error(subscription.senderEmail, "Hata oluştu"))
                lastAction?.invoke()
            }
        }
    }

    fun undoLastAction() {
        lastAction?.invoke()
        lastAction = null
    }

    fun finalizeLastAction() {
        lastAction = null
    }

    private fun getNextDateRange(): Pair<Calendar, Calendar> {
        val endDate = (lastEndDate?.clone() as? Calendar) ?: Calendar.getInstance()
        val startDate = endDate.clone() as Calendar
        startDate.add(Calendar.DAY_OF_YEAR, -30)
        return Pair(startDate, endDate)
    }
    
    fun resetToIdleState() {
        _signInState.tryEmit(SignInState.Idle)
        _scanState.tryEmit(ScanState.Idle)
        _loadMoreState.tryEmit(LoadMoreState.Idle)
        allSubscriptions.clear()
        lastEndDate = null
        isLoadingMore = false
        noMoreData = false
    }
}