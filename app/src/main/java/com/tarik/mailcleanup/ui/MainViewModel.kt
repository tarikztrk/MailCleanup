package com.tarik.mailcleanup.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.data.EmailRepository
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.data.UnsubscribeAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    
    // --- YENİ: ARAMA YÖNETİMİ ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    // --- GÜNCELLENMİŞ: allSubscriptions için bir StateFlow oluşturuyoruz ---
    private val _allSubscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    
    // --- GÜNCELLENMİŞ: scanState artık diğer Flow'ların birleşiminden oluşacak ---
    // Bu, arama terimi veya ana liste değiştiğinde otomatik olarak güncellenen bir sonuç Flow'u yaratır.
    val scanState: kotlinx.coroutines.flow.Flow<ScanState> = 
        combine(_allSubscriptions, _searchQuery) { subscriptions, query ->
            if (query.isBlank()) {
                ScanState.Success(subscriptions) // Arama boşsa, tüm listeyi göster
            } else {
                val filteredList = subscriptions.filter {
                    it.senderName.contains(query, ignoreCase = true) || 
                    it.senderEmail.contains(query, ignoreCase = true)
                }
                ScanState.Success(filteredList) // Arama varsa, filtrelenmiş listeyi göster
            }
        }
    
    private val _unsubscribeState = MutableSharedFlow<UnsubscribeState>()
    val unsubscribeState = _unsubscribeState.asSharedFlow()
    private val _keepState = MutableSharedFlow<KeepState>()
    val keepState = _keepState.asSharedFlow()
    private val _loadMoreState = MutableSharedFlow<LoadMoreState>(replay = 1)
    val loadMoreState = _loadMoreState.asSharedFlow()

    // --- YENİ: ÇOKLU SEÇİM YÖNETİMİ ---
    private val _selectedItems = MutableLiveData<Set<Subscription>>(emptySet())
    val selectedItems: LiveData<Set<Subscription>> = _selectedItems

    private val _isSelectionMode = MutableLiveData<Boolean>(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    // --- YENİ: Toplu işlem sonuçlarını bildirmek için ---
    private val _bulkActionResult = MutableSharedFlow<String>()
    val bulkActionResult = _bulkActionResult.asSharedFlow()

    // --- YENİ: GECİKMELİ İŞLEM MANTIĞI ---
    private var pendingJob: Job? = null // Gerçek işlemi tutacak olan Coroutine Job'ı
    private var lastRemovedSubscription: Subscription? = null
    private var lastRemovedSubscriptionIndex: Int = -1
    private var lastActionType: String? = null // "KEEP" veya "UNSUBSCRIBE"
    private var pendingCleanEmails: Boolean = false

    private var lastEndDate: Calendar? = null
    private var isLoadingMore = false
    private var noMoreData = false

    init {
        resetToIdleState()
    }

    private fun updateSubscriptionList(list: List<Subscription>) {
        _allSubscriptions.value = list.sortedByDescending { it.emailCount }
    }
    
    // YENİ FONKSİYON
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onSignInStarted() {
        _signInState.tryEmit(SignInState.InProgress)
    }

    fun onSignInSuccess(displayName: String?) {
        _signInState.tryEmit(SignInState.Success(displayName ?: getApplication<Application>().getString(
            R.string.default_user_name)))
    }

    fun onSignInFailed(errorMessage: String?) {
        _signInState.tryEmit(SignInState.Error(errorMessage ?: getApplication<Application>().getString(R.string.error_generic)))
    }

    fun startSubscriptionScan(account: GoogleSignInAccount) {
        // Taramayı sıfırlarken tüm bayrakları temizle
        resetToIdleState()
        
        viewModelScope.launch {
            try {
                val (startDate, endDate) = getNextDateRange()
                val subscriptions = repository.getSubscriptions(account, startDate, endDate)
                updateSubscriptionList(subscriptions)
                lastEndDate = startDate // Bir sonraki aralık için başlangıç tarihi olacak
                Log.d("MainViewModel", "İlk tarama tamamlandı: ${subscriptions.size} abonelik bulundu")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Abonelik tarama hatası", e)
                _allSubscriptions.value = emptyList()
                 
            }
        }
    }

    fun loadMoreSubscriptions(account: GoogleSignInAccount) {
        // --- BU KONTROL EN BAŞTA OLMALI ---
        if (isLoadingMore || noMoreData) {
            Log.d("ViewModelDebug", "loadMoreSubscriptions çağrıldı ama zaten yükleniyor veya veri bitti. isLoadingMore: $isLoadingMore, noMoreData: $noMoreData")
            // Eğer zaten veri yoksa ve tekrar çağrıldıysa,
            // ProgressBar'ın kapalı olduğundan emin olmak için NoMoreData'yı tekrar gönder.
            if (noMoreData) {
                Log.d("ViewModelDebug", "NoMoreData durumunu tekrar gönderiyorum (ProgressBar'ı kapatmak için)")
                _loadMoreState.tryEmit(LoadMoreState.NoMoreData)
            }
            return
        }

        viewModelScope.launch {
            isLoadingMore = true
            
            try {
                // Tarih aralığını al ve kontrol et
                val (startDate, endDate) = getNextDateRange()
                val oneYearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }
                
                if (startDate.before(oneYearAgo)) {
                    Log.d("ViewModelDebug", "Tarama 1 yıl sınırına ulaştı. Direkt NoMoreData gönderiliyor.")
                    noMoreData = true
                    Log.d("ViewModelDebug", "STATE -> NoMoreData")
                    _loadMoreState.tryEmit(LoadMoreState.NoMoreData)
                } else {
                    // Sadece gerçek bir işlem yapacaksak InProgress gönder
                    Log.d("ViewModelDebug", "STATE -> InProgress")
                    _loadMoreState.tryEmit(LoadMoreState.InProgress)
                    
                    val newSubscriptions = repository.getSubscriptions(account, startDate, endDate)
                    
                    if (newSubscriptions.isNotEmpty()) {
                        // Sadece gerçekten yeni olanları ekle
                        val currentSubscriptions = _allSubscriptions.value
                        val currentEmails = currentSubscriptions.map { it.senderEmail }.toSet()
                        val trulyNewItems = newSubscriptions.filter { !currentEmails.contains(it.senderEmail) }
                        
                        val combinedList = currentSubscriptions + trulyNewItems
                        updateSubscriptionList(combinedList)
                        
                        lastEndDate = startDate // Bitiş tarihi artık bir sonraki aralığın başlangıcı olacak
                        Log.d("ViewModelDebug", "STATE -> Success")
                        _loadMoreState.tryEmit(LoadMoreState.Success)
                        Log.d("MainViewModel", "Daha fazla yükleme tamamlandı: ${trulyNewItems.size} yeni abonelik eklendi.")
                    } else {
                        noMoreData = true
                        Log.d("ViewModelDebug", "STATE -> NoMoreData (Yeni abonelik bulunamadı)")
                        _loadMoreState.tryEmit(LoadMoreState.NoMoreData)
                        Log.d("MainViewModel", "Bu periyotta yeni abonelik bulunamadı, tarama tamamlanıyor.")
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModelDebug", "STATE -> Error: ${e.message}")
                _loadMoreState.tryEmit(LoadMoreState.Error(getApplication<Application>().getString(R.string.error_generic)))
            } finally {
                isLoadingMore = false
            }
        }
    }

    // --- UNSUBSCRIBE VE KEEP FONKSİYONLARI TAMAMEN YENİLENDİ ---

    fun unsubscribeAndClean(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean) {
        // Bekleyen bir eylem varsa, onu hemen tamamla ki çakışmasın.
        finalizePendingAction()

        // Adım 1: Öğeyi sadece UI'dan (yerel listeden) geçici olarak çıkar.
        val currentList = _allSubscriptions.value.toMutableList()
        lastRemovedSubscriptionIndex = currentList.indexOfFirst { it.senderEmail == subscription.senderEmail }
        if (lastRemovedSubscriptionIndex != -1) {
            lastRemovedSubscription = currentList.removeAt(lastRemovedSubscriptionIndex)
            lastActionType = "UNSUBSCRIBE"
            pendingCleanEmails = cleanEmails
            updateSubscriptionList(currentList)

            // Adım 2: Gerçek işlemi yapacak olan Coroutine'i bir Job olarak planla, ama HENÜZ BAŞLATMA.
            pendingJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                // Bu kod bloğu, sadece biz .start() veya .join() dediğimizde çalışacak.
                _unsubscribeState.emit(UnsubscribeState.InProgress(subscription.senderEmail))
                val action = repository.unsubscribeAndClean(account, subscription, cleanEmails)
                if (action is UnsubscribeAction.NotFound) {
                    _unsubscribeState.emit(UnsubscribeState.Error(subscription.senderEmail, getApplication<Application>().getString(R.string.error_no_unsubscribe_method)))
                    undoLastAction(informUser = false) // Kullanıcıya tekrar bildirmeden geri al
                } else {
                    _unsubscribeState.emit(UnsubscribeState.Success(subscription.senderEmail, action))
                }
            }
            
            // Adım 3: UI'a, geri alma seçeneği sunması için sinyal gönder.
            viewModelScope.launch { 
                _unsubscribeState.emit(UnsubscribeState.Success(subscription.senderEmail, UnsubscribeAction.NotFound)) // Geçici sinyal
            }
        }
    }
    
    fun keepSubscription(subscription: Subscription) {
        finalizePendingAction()

        val currentList = _allSubscriptions.value.toMutableList()
        lastRemovedSubscriptionIndex = currentList.indexOfFirst { it.senderEmail == subscription.senderEmail }
        if (lastRemovedSubscriptionIndex != -1) {
            lastRemovedSubscription = currentList.removeAt(lastRemovedSubscriptionIndex)
            lastActionType = "KEEP"
            updateSubscriptionList(currentList)
            
            pendingJob = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                val success = repository.keepSubscription(subscription)
                if (!success) {
                    _keepState.emit(KeepState.Error(subscription.senderEmail, getApplication<Application>().getString(R.string.error_keep_failed)))
                    undoLastAction(informUser = false)
                }
            }

            // UI'a, geri alma seçeneği sunması için sinyal gönder.
            viewModelScope.launch { 
                _keepState.emit(KeepState.Success(subscription.senderEmail))
            }
        }
    }
    
    fun undoLastAction(informUser: Boolean = true) {
        pendingJob?.cancel() // Arka planda çalışacak olan asıl işlemi tamamen iptal et.
        pendingJob = null
        
        lastRemovedSubscription?.let {
            if (lastRemovedSubscriptionIndex != -1) {
                val currentList = _allSubscriptions.value.toMutableList()
                currentList.add(lastRemovedSubscriptionIndex, it)
                updateSubscriptionList(currentList)
            }
        }
        
        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1
        lastActionType = null
        pendingCleanEmails = false

        if (informUser) {
            // Geri alındığını kullanıcıya bildirebiliriz.
            viewModelScope.launch { _keepState.emit(KeepState.Idle) } // Veya özel bir UndoState
        }
    }

    fun finalizePendingAction() {
        // Snackbar süresi dolduğunda bu fonksiyon çağrılır.
        // Bekleyen bir iş varsa, onu şimdi başlat.
        pendingJob?.start()
        
        // Geçici verileri temizle
        pendingJob = null
        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1
        lastActionType = null
        pendingCleanEmails = false
    }

    private fun getNextDateRange(): Pair<Calendar, Calendar> {
        // Bir sonraki periyodun başlangıcı, bir öncekinin bitişidir.
        val endDate = (lastEndDate?.clone() as? Calendar) ?: Calendar.getInstance()
        val startDate = endDate.clone() as Calendar
        startDate.add(Calendar.DAY_OF_YEAR, -30)
        return Pair(startDate, endDate)
    }
    
    // --- ÇOKLU SEÇİM FONKSİYONLARI ---
    fun toggleSelection(subscription: Subscription) {
        val currentSelection = _selectedItems.value ?: emptySet()
        val newSelection = currentSelection.toMutableSet()
        
        if (newSelection.contains(subscription)) {
            newSelection.remove(subscription)
        } else {
            newSelection.add(subscription)
        }

        _selectedItems.value = newSelection
        _isSelectionMode.value = newSelection.isNotEmpty()
    }

    fun startSelectionMode(subscription: Subscription) {
        _isSelectionMode.value = true
        toggleSelection(subscription)
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isSelectionMode.value = false
    }

    fun selectAll() {
        _selectedItems.value = _allSubscriptions.value.toSet()
    }

    fun getSelectedItemsCount(): Int {
        return _selectedItems.value?.size ?: 0
    }

    // --- YENİ: TOPLU İŞLEM FONKSİYONLARI ---

    fun bulkKeepSelected() {
        // SEÇİLİ ÖĞELERİ İŞLEM BAŞINDA BİR DEĞİŞKENE ALIYORUZ.
        val itemsToKeep = _selectedItems.value ?: return
        if (itemsToKeep.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            
            // Önce UI'ı güncelle
            val currentList = _allSubscriptions.value.toMutableList()
            currentList.removeAll(itemsToKeep)
            updateSubscriptionList(currentList)

            // Arka planda veritabanı işlemlerini yap
            itemsToKeep.forEach { subscription ->
                if (repository.keepSubscription(subscription)) {
                    successCount++
                }
            }

            // İşlem sonucunu bildir
            _bulkActionResult.emit(getApplication<Application>().getString(R.string.bulk_action_result_kept, successCount))
            
            // İŞLEM BİTTİKTEN SONRA SEÇİMİ TEMİZLE
            clearSelection()
        }
    }

    fun bulkUnsubscribeSelected(account: GoogleSignInAccount?, cleanEmails: Boolean) {
        if (account == null) return
        // SEÇİLİ ÖĞELERİ İŞLEM BAŞINDA BİR DEĞİŞKENE ALIYORUZ.
        val itemsToUnsubscribe = _selectedItems.value ?: return
        if (itemsToUnsubscribe.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            
            // Önce UI'ı güncelle
            val currentList = _allSubscriptions.value.toMutableList()
            currentList.removeAll(itemsToUnsubscribe)
            updateSubscriptionList(currentList)
            
            itemsToUnsubscribe.forEach { subscription ->
                val action = repository.unsubscribeAndClean(account, subscription, cleanEmails)
                if (action !is UnsubscribeAction.NotFound) {
                    successCount++
                }
                // Not: Toplu çıkışta her bir öğe için ayrı HTTP linki açmak pratik değil.
                // Bu yüzden sadece mailto ile olanlar sessizce işlenir.
            }

            _bulkActionResult.emit(getApplication<Application>().getString(R.string.bulk_action_result_unsubscribed, successCount))
            
            // İŞLEM BİTTİKTEN SONRA SEÇİMİ TEMİZLE
            clearSelection()
        }
    }

    fun resetToIdleState() {
        _signInState.tryEmit(SignInState.Idle)
        _loadMoreState.tryEmit(LoadMoreState.Idle)
        _allSubscriptions.value = emptyList()
        _searchQuery.value = ""
        lastEndDate = null
        isLoadingMore = false
        noMoreData = false
        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1
        lastActionType = null
        pendingCleanEmails = false
        pendingJob?.cancel()
        pendingJob = null
        // Seçim durumunu da sıfırla
        clearSelection()
    }
}