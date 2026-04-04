package com.tarik.mailcleanup.ui

import android.util.Log
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
import com.tarik.mailcleanup.domain.usecase.KeepSubscriptionUseCase
import com.tarik.mailcleanup.domain.usecase.UnsubscribeAndCleanUseCase
import com.tarik.mailcleanup.ui.paging.SubscriptionPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SignInUiStatus {
    data object Idle : SignInUiStatus
    data object InProgress : SignInUiStatus
    data object Success : SignInUiStatus
    data class Error(val message: String) : SignInUiStatus
}

sealed interface ScanUiStatus {
    data object Idle : ScanUiStatus
    data object InProgress : ScanUiStatus
    data object Success : ScanUiStatus
    data class Error(val message: String) : ScanUiStatus
}

data class MainUiState(
    val signInStatus: SignInUiStatus = SignInUiStatus.Idle,
    val scanStatus: ScanUiStatus = ScanUiStatus.Idle,
    val searchQuery: String = "",
    val processingEmail: String? = null,
    val selectedItems: Set<Subscription> = emptySet(),
    val hiddenEmails: Set<String> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedItems.isNotEmpty()
}

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
    private val unsubscribeAndCleanUseCase: UnsubscribeAndCleanUseCase,
    private val keepSubscriptionUseCase: KeepSubscriptionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MainUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val signedInAccount = MutableStateFlow<MailAccount?>(null)
    private val visibleSubscriptions = MutableStateFlow<List<Subscription>>(emptyList())

    val pagedSubscriptions: Flow<PagingData<Subscription>> = combine(
        signedInAccount
            .filterNotNull()
            .flatMapLatest { account ->
                Pager(
                    config = PagingConfig(
                        pageSize = 30,
                        prefetchDistance = 5,
                        enablePlaceholders = false
                    ),
                    pagingSourceFactory = {
                        SubscriptionPagingSource(account, getSubscriptionsUseCase)
                    }
                ).flow
            }
            .cachedIn(viewModelScope),
        _uiState
    ) { pagingData, state ->
        pagingData.filter { subscription ->
            val matchesQuery = state.searchQuery.isBlank() ||
                subscription.senderName.contains(state.searchQuery, ignoreCase = true) ||
                subscription.senderEmail.contains(state.searchQuery, ignoreCase = true)

            matchesQuery && !state.hiddenEmails.contains(subscription.senderEmail)
        }
    }

    private var pendingJob: Job? = null
    private var lastRemovedSubscription: Subscription? = null

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateVisibleSubscriptions(items: List<Subscription>) {
        visibleSubscriptions.value = items
    }

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
        _uiState.update {
            it.copy(
                scanStatus = ScanUiStatus.InProgress,
                processingEmail = null,
                selectedItems = emptySet(),
                hiddenEmails = emptySet(),
                searchQuery = ""
            )
        }

        signedInAccount.value = account
        _uiState.update { it.copy(scanStatus = ScanUiStatus.Success) }
    }

    fun unsubscribeAndClean(account: MailAccount, subscription: Subscription, cleanEmails: Boolean) {
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
                    val message = if (action is UnsubscribeAction.MailTo) {
                        stringProvider.get(R.string.snackbar_unsubscribed_mailto, subscription.senderEmail)
                    } else {
                        stringProvider.get(R.string.snackbar_unsubscribed_success)
                    }
                    _uiEvent.emit(MainUiEvent.ShowUndo(message))
                    if (action is UnsubscribeAction.Http) {
                        _uiEvent.emit(MainUiEvent.OpenUrl(action.url))
                    }
                }
                is DomainResult.Error -> {
                    undoLastAction(informUser = false)
                    _uiEvent.emit(MainUiEvent.ShowError(domainErrorToMessage(result.error)))
                }
            }
        }

        viewModelScope.launch {
            _uiEvent.emit(MainUiEvent.ShowUndo(stringProvider.get(R.string.snackbar_unsubscribed_success)))
        }
    }

    fun keepSubscription(subscription: Subscription) {
        finalizePendingAction()

        lastRemovedSubscription = subscription
        _uiState.update {
            it.copy(
                hiddenEmails = it.hiddenEmails + subscription.senderEmail,
                selectedItems = it.selectedItems - subscription
            )
        }

        pendingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            when (val result = keepSubscriptionUseCase(subscription)) {
                is DomainResult.Success -> Unit
                is DomainResult.Error -> {
                    undoLastAction(informUser = false)
                    _uiEvent.emit(MainUiEvent.ShowError("'${subscription.senderEmail}' icin hata: ${domainErrorToMessage(result.error)}"))
                }
            }
        }

        viewModelScope.launch {
            _uiEvent.emit(MainUiEvent.ShowUndo(stringProvider.get(R.string.snackbar_kept, subscription.senderEmail)))
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
            viewModelScope.launch { _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.snackbar_undo))) }
        }
    }

    fun finalizePendingAction() {
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

    fun selectAll() {
        _uiState.update { it.copy(selectedItems = visibleSubscriptions.value.toSet()) }
    }

    fun getSelectedItemsCount(): Int = _uiState.value.selectedItems.size

    fun bulkKeepSelected() {
        val itemsToKeep = _uiState.value.selectedItems
        if (itemsToKeep.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    hiddenEmails = it.hiddenEmails + itemsToKeep.map { item -> item.senderEmail }.toSet(),
                    selectedItems = emptySet()
                )
            }

            var successCount = 0
            itemsToKeep.forEach {
                if (keepSubscriptionUseCase(it) is DomainResult.Success) successCount++
            }
            _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.bulk_action_result_kept, successCount)))
        }
    }

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
            _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.bulk_action_result_unsubscribed, successCount)))
        }
    }

    fun resetToIdleState() {
        pendingJob?.cancel()
        pendingJob = null
        lastRemovedSubscription = null
        visibleSubscriptions.value = emptyList()
        signedInAccount.value = null
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
}
