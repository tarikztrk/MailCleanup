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

    // --- YENİ: Geri Alma Mantığı İçin ---
    private var lastAction: (() -> Unit)? = null

    // --- YENİ: Dinamik Tarih Aralığı Yönetimi ---
    private var lastEndDate: Calendar? = null
    private var isLoadingMore = false
    private var noMoreData = false

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
        // Taramayı sıfırla
        lastEndDate = null
        isLoadingMore = false
        noMoreData = false
        _loadMoreState.tryEmit(LoadMoreState.Idle)
        
        viewModelScope.launch {
            _scanState.tryEmit(ScanState.InProgress)
            try {
                // İlk periyodu yükle
                val (startDate, endDate) = getNextDateRange()
                val subscriptions = repository.getSubscriptions(account, startDate, endDate)
                // Sıralamayı burada yap
                val sortedSubscriptions = subscriptions.sortedByDescending { it.emailCount }
                _scanState.tryEmit(ScanState.Success(sortedSubscriptions))
                lastEndDate = endDate
                Log.d("MainViewModel", "İlk tarama tamamlandı: ${subscriptions.size} abonelik bulundu")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Abonelik tarama hatası", e)
                _scanState.tryEmit(ScanState.Error("Abonelikler taranırken bir hata oluştu."))
            }
        }
    }

    fun resetToIdleState() {
        _signInState.tryEmit(SignInState.Idle)
        _scanState.tryEmit(ScanState.Idle)
        _loadMoreState.tryEmit(LoadMoreState.Idle)
        lastEndDate = null
        isLoadingMore = false
        noMoreData = false
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

    // YENİ FONKSİYON: Daha fazla abonelik yükle - Tutarlı sıralama ile
    fun loadMoreSubscriptions(account: GoogleSignInAccount) {
        if (isLoadingMore || noMoreData) return

        viewModelScope.launch {
            isLoadingMore = true
            _loadMoreState.tryEmit(LoadMoreState.InProgress)
            
            try {
                val (startDate, endDate) = getNextDateRange()
                val newSubscriptions = repository.getSubscriptions(account, startDate, endDate)
                
                if (newSubscriptions.isNotEmpty()) {
                    val currentState = scanState.replayCache.firstOrNull()
                    if (currentState is ScanState.Success) {
                        
                        // Tutarlı sıralama için basit ekleme
                        val currentMap = currentState.subscriptions.associateBy { it.senderEmail }.toMutableMap()
                        val newItemsToAdd = mutableListOf<Subscription>()

                        newSubscriptions.forEach { newItem ->
                            if (currentMap.containsKey(newItem.senderEmail)) {
                                // Var olanı güncelle (eğer gerekirse) - şimdilik atlıyoruz
                            } else {
                                // Sadece gerçekten yeni olanları ekle
                                newItemsToAdd.add(newItem)
                            }
                        }
                        
                        // Yeni öğeleri mevcut listenin sonuna ekle (sıralamayı bozmadan)
                        val finalList = currentState.subscriptions + newItemsToAdd
                        _scanState.tryEmit(ScanState.Success(finalList))
                    }
                    lastEndDate = endDate
                    _loadMoreState.tryEmit(LoadMoreState.Success)
                    Log.d("MainViewModel", "Daha fazla yükleme tamamlandı: ${newSubscriptions.size} yeni abonelik bulundu.")
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
    
    // Tarih aralığı hesaplayan yardımcı fonksiyon
    private fun getNextDateRange(): Pair<Calendar, Calendar> {
        val endDate = (lastEndDate?.clone() as? Calendar) ?: Calendar.getInstance()
        val startDate = endDate.clone() as Calendar
        startDate.add(Calendar.DAY_OF_YEAR, -30)
        return Pair(startDate, endDate)
    }
}