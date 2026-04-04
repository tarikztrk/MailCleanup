package com.tarik.mailcleanup.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarik.mailcleanup.core.text.StringProvider
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.domain.model.DomainError
import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.usecase.GetSubscriptionsUseCase
import com.tarik.mailcleanup.domain.usecase.KeepSubscriptionUseCase
import com.tarik.mailcleanup.domain.usecase.UnsubscribeAndCleanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
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
    val subscriptions: List<Subscription> = emptyList(),
    val filteredSubscriptions: List<Subscription> = emptyList(),
    val searchQuery: String = "",
    val processingEmail: String? = null,
    val isLoadingMore: Boolean = false,
    val isLastPage: Boolean = false,
    val selectedItems: Set<Subscription> = emptySet()
) {
    val isSelectionMode: Boolean get() = selectedItems.isNotEmpty()
}

sealed interface MainUiEvent {
    data class ShowUndo(val message: String) : MainUiEvent
    data class ShowError(val message: String) : MainUiEvent
    data class ShowMessage(val message: String) : MainUiEvent
    data class OpenUrl(val url: String) : MainUiEvent
}

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

    private var pendingJob: Job? = null
    private var lastRemovedSubscription: Subscription? = null
    private var lastRemovedSubscriptionIndex: Int = -1

    private var lastEndDate: Calendar? = null

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    fun onSignInStarted() {
        _uiState.update { it.copy(signInStatus = SignInUiStatus.InProgress) }
    }

    fun onSignInSuccess(displayName: String?) {
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

    fun startSubscriptionScan(account: GoogleSignInAccount) {
        _uiState.update {
            it.copy(
                scanStatus = ScanUiStatus.InProgress,
                processingEmail = null,
                isLoadingMore = false,
                isLastPage = false,
                subscriptions = emptyList(),
                filteredSubscriptions = emptyList(),
                selectedItems = emptySet(),
                searchQuery = ""
            )
        }
        lastEndDate = null

        viewModelScope.launch {
            val (startDate, endDate) = getNextDateRange()
            when (val result = getSubscriptionsUseCase(account, startDate, endDate)) {
                is DomainResult.Success -> {
                    val subscriptions = result.data.sortedByDescending { it.emailCount }
                    lastEndDate = startDate
                    _uiState.update {
                        it.copy(
                            scanStatus = ScanUiStatus.Success,
                            subscriptions = subscriptions,
                            filteredSubscriptions = subscriptions
                        )
                    }
                }
                is DomainResult.Error -> {
                    Log.e("MainViewModel", "Abonelik tarama hatası: ${result.error}")
                    _uiState.update {
                        it.copy(
                            scanStatus = ScanUiStatus.Error(domainErrorToMessage(result.error)),
                            subscriptions = emptyList(),
                            filteredSubscriptions = emptyList()
                        )
                    }
                }
            }
        }
    }

    fun loadMoreSubscriptions(account: GoogleSignInAccount) {
        val current = _uiState.value
        if (current.isLoadingMore || current.isLastPage) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val (startDate, endDate) = getNextDateRange()
                val oneYearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }

                if (startDate.before(oneYearAgo)) {
                    _uiState.update { it.copy(isLoadingMore = false, isLastPage = true) }
                    _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.snackbar_all_loaded)))
                    return@launch
                }

                when (val result = getSubscriptionsUseCase(account, startDate, endDate)) {
                    is DomainResult.Success -> {
                        val newSubscriptions = result.data
                        if (newSubscriptions.isNotEmpty()) {
                            val existing = _uiState.value.subscriptions
                            val existingEmails = existing.map { it.senderEmail }.toSet()
                            val merged = (existing + newSubscriptions.filterNot { existingEmails.contains(it.senderEmail) })
                                .sortedByDescending { it.emailCount }

                            lastEndDate = startDate
                            _uiState.update {
                                it.copy(
                                    isLoadingMore = false,
                                    subscriptions = merged,
                                    selectedItems = it.selectedItems.filter { s -> merged.any { item -> item.senderEmail == s.senderEmail } }.toSet()
                                )
                            }
                            applyFilter()
                        } else {
                            _uiState.update { it.copy(isLoadingMore = false, isLastPage = true) }
                            _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.snackbar_all_loaded)))
                        }
                    }
                    is DomainResult.Error -> {
                        _uiState.update { it.copy(isLoadingMore = false) }
                        _uiEvent.emit(MainUiEvent.ShowError(domainErrorToMessage(result.error)))
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "load more error", e)
                _uiState.update { it.copy(isLoadingMore = false) }
                _uiEvent.emit(MainUiEvent.ShowError(stringProvider.get(R.string.error_generic)))
            }
        }
    }

    fun unsubscribeAndClean(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean) {
        finalizePendingAction()

        val currentList = _uiState.value.subscriptions.toMutableList()
        lastRemovedSubscriptionIndex = currentList.indexOfFirst { it.senderEmail == subscription.senderEmail }
        if (lastRemovedSubscriptionIndex == -1) return

        lastRemovedSubscription = currentList.removeAt(lastRemovedSubscriptionIndex)
        _uiState.update {
            it.copy(
                subscriptions = currentList,
                processingEmail = null,
                selectedItems = it.selectedItems - subscription
            )
        }
        applyFilter()

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

        val currentList = _uiState.value.subscriptions.toMutableList()
        lastRemovedSubscriptionIndex = currentList.indexOfFirst { it.senderEmail == subscription.senderEmail }
        if (lastRemovedSubscriptionIndex == -1) return

        lastRemovedSubscription = currentList.removeAt(lastRemovedSubscriptionIndex)
        _uiState.update {
            it.copy(
                subscriptions = currentList,
                selectedItems = it.selectedItems - subscription
            )
        }
        applyFilter()

        pendingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            when (val result = keepSubscriptionUseCase(subscription)) {
                is DomainResult.Success -> Unit
                is DomainResult.Error -> {
                    undoLastAction(informUser = false)
                    _uiEvent.emit(MainUiEvent.ShowError("'${subscription.senderEmail}' için hata: ${domainErrorToMessage(result.error)}"))
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
            if (lastRemovedSubscriptionIndex != -1) {
                val updated = _uiState.value.subscriptions.toMutableList()
                updated.add(lastRemovedSubscriptionIndex, removed)
                _uiState.update { it.copy(subscriptions = updated.sortedByDescending { item -> item.emailCount }) }
                applyFilter()
            }
        }

        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1

        if (informUser) {
            viewModelScope.launch { _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.snackbar_undo))) }
        }
    }

    fun finalizePendingAction() {
        pendingJob?.start()
        pendingJob = null
        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1
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
        _uiState.update { it.copy(selectedItems = it.filteredSubscriptions.toSet()) }
    }

    fun getSelectedItemsCount(): Int = _uiState.value.selectedItems.size

    fun bulkKeepSelected() {
        val itemsToKeep = _uiState.value.selectedItems
        if (itemsToKeep.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            val updated = _uiState.value.subscriptions.toMutableList()
            updated.removeAll(itemsToKeep)
            _uiState.update { it.copy(subscriptions = updated, selectedItems = emptySet()) }
            applyFilter()

            itemsToKeep.forEach {
                if (keepSubscriptionUseCase(it) is DomainResult.Success) successCount++
            }
            _uiEvent.emit(MainUiEvent.ShowMessage(stringProvider.get(R.string.bulk_action_result_kept, successCount)))
        }
    }

    fun bulkUnsubscribeSelected(account: GoogleSignInAccount?, cleanEmails: Boolean) {
        if (account == null) return
        val itemsToUnsubscribe = _uiState.value.selectedItems
        if (itemsToUnsubscribe.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            val updated = _uiState.value.subscriptions.toMutableList()
            updated.removeAll(itemsToUnsubscribe)
            _uiState.update { it.copy(subscriptions = updated, selectedItems = emptySet()) }
            applyFilter()

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
        lastRemovedSubscriptionIndex = -1
        lastEndDate = null
        _uiState.update { MainUiState() }
    }

    private fun applyFilter() {
        _uiState.update { state ->
            val filtered = if (state.searchQuery.isBlank()) {
                state.subscriptions
            } else {
                state.subscriptions.filter {
                    it.senderName.contains(state.searchQuery, ignoreCase = true) ||
                        it.senderEmail.contains(state.searchQuery, ignoreCase = true)
                }
            }
            state.copy(filteredSubscriptions = filtered)
        }
    }

    private fun getNextDateRange(): Pair<Calendar, Calendar> {
        val endDate = (lastEndDate?.clone() as? Calendar) ?: Calendar.getInstance()
        val startDate = endDate.clone() as Calendar
        startDate.add(Calendar.DAY_OF_YEAR, -30)
        return Pair(startDate, endDate)
    }

    private fun domainErrorToMessage(error: DomainError): String {
        return when (error) {
            DomainError.Generic -> stringProvider.get(R.string.error_generic)
            DomainError.NoUnsubscribeMethod -> stringProvider.get(R.string.error_no_unsubscribe_method)
            DomainError.KeepFailed -> stringProvider.get(R.string.error_keep_failed)
        }
    }
}
