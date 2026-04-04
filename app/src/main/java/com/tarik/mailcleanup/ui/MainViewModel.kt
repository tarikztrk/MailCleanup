package com.tarik.mailcleanup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.core.text.StringProvider
import com.tarik.mailcleanup.domain.model.DomainError
import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.usecase.GetSubscriptionsUseCase
import com.tarik.mailcleanup.domain.usecase.UnsubscribeAndCleanUseCase
import com.tarik.mailcleanup.ui.paging.SubscriptionPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SubscriptionFilter {
    ALL,
    NEWSLETTERS,
    PROMOTIONS
}

enum class SubscriptionSort {
    MOST_FREQUENT,
    A_TO_Z
}

/**
 * Sign-in buton durumunu ayrı temsil eder.
 */
sealed interface SignInUiStatus {
    data object Idle : SignInUiStatus
    data object InProgress : SignInUiStatus
    data object Success : SignInUiStatus
    data class Error(val message: String) : SignInUiStatus
}

/**
 * Abonelik tarama/yükleme durumunu temsil eder.
 */
sealed interface ScanUiStatus {
    data object Idle : ScanUiStatus
    data object InProgress : ScanUiStatus
    data object Success : ScanUiStatus
    data class Error(val message: String) : ScanUiStatus
}

/**
 * Ekranın tek durum kaynağı.
 * Fragment/Activity sadece bu modeli render eder.
 */
data class MainUiState(
    val signInStatus: SignInUiStatus = SignInUiStatus.Idle,
    val scanStatus: ScanUiStatus = ScanUiStatus.Idle,
    val searchQuery: String = "",
    val processingEmail: String? = null,
    val selectedItems: Set<Subscription> = emptySet(),
    val hiddenEmails: Set<String> = emptySet(),
    val currentAccount: MailAccount? = null,
    val selectedFilter: SubscriptionFilter = SubscriptionFilter.ALL,
    val selectedSort: SubscriptionSort = SubscriptionSort.MOST_FREQUENT
) {
    val isSelectionMode: Boolean get() = selectedItems.isNotEmpty()
}

/**
 * Tek-seferlik UI aksiyonları (Snackbar, URL açma vb.).
 */
sealed interface MainUiEvent {
    data class ShowUndo(val message: String) : MainUiEvent
    data class ShowError(val message: String) : MainUiEvent
    data class ShowMessage(val message: String) : MainUiEvent
    data class OpenUrl(val url: String) : MainUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    private val stringProvider: StringProvider,
    private val getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val unsubscribeAndCleanUseCase: UnsubscribeAndCleanUseCase
) : ViewModel() {

    // Ekranın ana state kaynağı.
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    // Tek seferlik event'ler state yerine channel üstünden iletilir.
    private val _uiEvent = Channel<MainUiEvent>(capacity = Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private val accountFlow = uiState
        .map { it.currentAccount }
        .distinctUntilChanged()
        .filterNotNull()

    private val filterFlow = uiState
        .map { Triple(it.searchQuery, it.hiddenEmails, it.selectedFilter) }
        .distinctUntilChanged()

    private val sortFlow = uiState
        .map { it.selectedSort }
        .distinctUntilChanged()

    val pagedSubscriptions: Flow<PagingData<Subscription>> = combine(
        combine(accountFlow, sortFlow) { account, sort -> account to sort }
            .flatMapLatest { account ->
                val (mailAccount, selectedSort) = account
                Pager(
                    config = PagingConfig(
                        pageSize = 30,
                        prefetchDistance = 5,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = {
                        SubscriptionPagingSource(mailAccount, selectedSort, getSubscriptionsUseCase)
                    }
                ).flow
            }
            .cachedIn(viewModelScope),
        filterFlow
    ) { pagingData, filterState ->
        // Paging stream üstünde client-side arama + gizleme filtresi uygulanır.
        val searchQuery = filterState.first
        val hiddenEmails = filterState.second
        val selectedFilter = filterState.third
        pagingData.filter { subscription ->
            val matchesQuery = searchQuery.isBlank() ||
                subscription.senderName.contains(searchQuery, ignoreCase = true) ||
                subscription.senderEmail.contains(searchQuery, ignoreCase = true)

            val matchesCategory = when (selectedFilter) {
                SubscriptionFilter.ALL -> true
                SubscriptionFilter.NEWSLETTERS -> looksLikeNewsletter(subscription)
                SubscriptionFilter.PROMOTIONS -> looksLikePromotion(subscription)
            }

            matchesQuery && matchesCategory && !hiddenEmails.contains(subscription.senderEmail)
        }
    }

    private var pendingJob: Job? = null
    private var lastRemovedSubscription: Subscription? = null

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: SubscriptionFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setSort(sort: SubscriptionSort) {
        _uiState.update { it.copy(selectedSort = sort) }
    }

    fun currentMailAccount(): MailAccount? = _uiState.value.currentAccount

    fun onSignInStarted() {
        _uiState.update { it.copy(signInStatus = SignInUiStatus.InProgress) }
    }

    fun onSignInSuccess() {
        _uiState.update { it.copy(signInStatus = SignInUiStatus.Success) }
    }

    fun onSignInFailed(errorMessage: String?) {
        _uiState.update {
            it.copy(
                signInStatus = SignInUiStatus.Error(errorMessage ?: stringProvider.get(R.string.error_generic)),
                scanStatus = ScanUiStatus.Idle
            )
        }
    }

    fun startSubscriptionScan(account: MailAccount) {
        // Hesap değiştiğinde ekran state'i temizlenir ve yeni paging kaynağı tetiklenir.
        _uiState.update {
            it.copy(
                scanStatus = ScanUiStatus.InProgress,
                processingEmail = null,
                selectedItems = emptySet(),
                hiddenEmails = emptySet(),
                searchQuery = "",
                currentAccount = account,
                selectedFilter = SubscriptionFilter.ALL,
                selectedSort = SubscriptionSort.MOST_FREQUENT
            )
        }

        _uiState.update { it.copy(scanStatus = ScanUiStatus.Success) }
    }

    fun unsubscribeAndClean(account: MailAccount, subscription: Subscription, cleanEmails: Boolean) {
        // Undo için aksiyonu önce UI'dan gizleyip işi gecikmeli başlatıyoruz.
        finalizePendingAction()

        lastRemovedSubscription = subscription
        _uiState.update {
            it.copy(
                hiddenEmails = it.hiddenEmails + subscription.senderEmail,
                selectedItems = it.selectedItems - subscription,
                processingEmail = null
            )
        }

        pendingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _uiState.update { it.copy(processingEmail = subscription.senderEmail) }
            val result = unsubscribeAndCleanUseCase(account, subscription, cleanEmails)
            _uiState.update { it.copy(processingEmail = null) }

            when (result) {
                is DomainResult.Success -> {
                    val action = result.data
                    if (action is UnsubscribeAction.MailTo) {
                        emitEvent(MainUiEvent.ShowMessage(stringProvider.get(R.string.snackbar_unsubscribed_mailto, subscription.senderEmail)))
                    }
                    if (action is UnsubscribeAction.Http) {
                        emitEvent(MainUiEvent.OpenUrl(action.url))
                    }
                }
                is DomainResult.Error -> {
                    // İşlem başarısızsa UI gizlemesini geri al.
                    undoLastAction(informUser = false)
                    emitEvent(MainUiEvent.ShowError(domainErrorToMessage(result.error)))
                }
            }
        }

        viewModelScope.launch {
            emitEvent(MainUiEvent.ShowUndo(stringProvider.get(R.string.snackbar_unsubscribed_success)))
        }
    }

    fun undoLastAction(informUser: Boolean = true) {
        pendingJob?.cancel()
        pendingJob = null

        lastRemovedSubscription?.let { removed ->
            _uiState.update { it.copy(hiddenEmails = it.hiddenEmails - removed.senderEmail) }
        }
        lastRemovedSubscription = null

        if (informUser) {
            viewModelScope.launch { emitEvent(MainUiEvent.ShowMessage(stringProvider.get(R.string.snackbar_undo))) }
        }
    }

    fun finalizePendingAction() {
        // Snackbar süresi dolduğunda bekleyen işi gerçekten çalıştırır.
        pendingJob?.start()
        pendingJob = null
        lastRemovedSubscription = null
    }

    fun toggleSelection(subscription: Subscription) {
        val selection = _uiState.value.selectedItems.toMutableSet()
        if (selection.contains(subscription)) selection.remove(subscription) else selection.add(subscription)
        _uiState.update { it.copy(selectedItems = selection) }
    }

    fun startSelectionMode(subscription: Subscription) {
        toggleSelection(subscription)
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    fun selectAll(items: List<Subscription>) {
        _uiState.update { it.copy(selectedItems = items.toSet()) }
    }

    fun getSelectedItemsCount(): Int = _uiState.value.selectedItems.size

    fun bulkUnsubscribeSelected(account: MailAccount?, cleanEmails: Boolean) {
        if (account == null) return
        val itemsToUnsubscribe = _uiState.value.selectedItems
        if (itemsToUnsubscribe.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hiddenEmails = it.hiddenEmails + itemsToUnsubscribe.map { item -> item.senderEmail }.toSet(),
                    selectedItems = emptySet()
                )
            }

            var successCount = 0
            itemsToUnsubscribe.forEach {
                if (unsubscribeAndCleanUseCase(account, it, cleanEmails) is DomainResult.Success) successCount++
            }
            emitEvent(MainUiEvent.ShowMessage(stringProvider.get(R.string.bulk_action_result_unsubscribed, successCount)))
        }
    }

    fun resetToIdleState() {
        pendingJob?.cancel()
        pendingJob = null
        lastRemovedSubscription = null
        _uiState.update { MainUiState() }
    }

    private fun domainErrorToMessage(error: DomainError): String {
        return when (error) {
            DomainError.Generic -> stringProvider.get(R.string.error_generic)
            DomainError.Auth -> stringProvider.get(R.string.error_auth)
            DomainError.RateLimit -> stringProvider.get(R.string.error_rate_limit)
            DomainError.Network -> stringProvider.get(R.string.error_network)
            DomainError.Server -> stringProvider.get(R.string.error_server)
            DomainError.NoUnsubscribeMethod -> stringProvider.get(R.string.error_no_unsubscribe_method)
            DomainError.KeepFailed -> stringProvider.get(R.string.error_keep_failed)
        }
    }

    private suspend fun emitEvent(event: MainUiEvent) {
        _uiEvent.send(event)
    }

    private fun looksLikeNewsletter(subscription: Subscription): Boolean {
        val text = "${subscription.senderName} ${subscription.senderEmail}".lowercase()
        val keywords = listOf("newsletter", "digest", "substack", "medium", "daily", "weekly", "brief")
        return keywords.any { keyword -> text.contains(keyword) }
    }

    private fun looksLikePromotion(subscription: Subscription): Boolean {
        val text = "${subscription.senderName} ${subscription.senderEmail}".lowercase()
        val keywords = listOf("promo", "sale", "deals", "offer", "discount", "campaign", "shop")
        return keywords.any { keyword -> text.contains(keyword) }
    }
}
