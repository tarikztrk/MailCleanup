package com.tarik.mailcleanup.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.R
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
    application: Application,
    private val getSubscriptionsUseCase: GetSubscriptionsUseCase,
    private val unsubscribeAndCleanUseCase: UnsubscribeAndCleanUseCase,
    private val keepSubscriptionUseCase: KeepSubscriptionUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<MainUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private var pendingJob: Job? = null
    private var lastRemovedSubscription: Subscription? = null
    private var lastRemovedSubscriptionIndex: Int = -1

    private var lastEndDate: Calendar? = null

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilter()
    }

    fun onSignInStarted() {
        _uiState.value = _uiState.value.copy(signInStatus = SignInUiStatus.InProgress)
    }

    fun onSignInSuccess(displayName: String?) {
        _uiState.value = _uiState.value.copy(signInStatus = SignInUiStatus.Success)
    }

    fun onSignInFailed(errorMessage: String?) {
        _uiState.value = _uiState.value.copy(
            signInStatus = SignInUiStatus.Error(errorMessage ?: getApplication<Application>().getString(R.string.error_generic)),
            scanStatus = ScanUiStatus.Idle
        )
    }

    fun startSubscriptionScan(account: GoogleSignInAccount) {
        val state = _uiState.value
        _uiState.value = state.copy(
            scanStatus = ScanUiStatus.InProgress,
            processingEmail = null,
            isLoadingMore = false,
            isLastPage = false,
            subscriptions = emptyList(),
            filteredSubscriptions = emptyList(),
            selectedItems = emptySet(),
            searchQuery = ""
        )
        lastEndDate = null

        viewModelScope.launch {
            try {
                val (startDate, endDate) = getNextDateRange()
                val subscriptions = getSubscriptionsUseCase(account, startDate, endDate).sortedByDescending { it.emailCount }
                lastEndDate = startDate

                _uiState.value = _uiState.value.copy(
                    scanStatus = ScanUiStatus.Success,
                    subscriptions = subscriptions,
                    filteredSubscriptions = subscriptions
                )
            } catch (e: Exception) {
                Log.e("MainViewModel", "Abonelik tarama hatası", e)
                _uiState.value = _uiState.value.copy(
                    scanStatus = ScanUiStatus.Error(getApplication<Application>().getString(R.string.error_generic)),
                    subscriptions = emptyList(),
                    filteredSubscriptions = emptyList()
                )
            }
        }
    }

    fun loadMoreSubscriptions(account: GoogleSignInAccount) {
        val current = _uiState.value
        if (current.isLoadingMore || current.isLastPage) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val (startDate, endDate) = getNextDateRange()
                val oneYearAgo = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }

                if (startDate.before(oneYearAgo)) {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, isLastPage = true)
                    _uiEvent.emit(MainUiEvent.ShowMessage(getApplication<Application>().getString(R.string.snackbar_all_loaded)))
                    return@launch
                }

                val newSubscriptions = getSubscriptionsUseCase(account, startDate, endDate)
                if (newSubscriptions.isNotEmpty()) {
                    val existing = _uiState.value.subscriptions
                    val existingEmails = existing.map { it.senderEmail }.toSet()
                    val merged = (existing + newSubscriptions.filterNot { existingEmails.contains(it.senderEmail) })
                        .sortedByDescending { it.emailCount }

                    lastEndDate = startDate
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        subscriptions = merged,
                        selectedItems = _uiState.value.selectedItems.filter { s -> merged.any { it.senderEmail == s.senderEmail } }.toSet()
                    )
                    applyFilter()
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false, isLastPage = true)
                    _uiEvent.emit(MainUiEvent.ShowMessage(getApplication<Application>().getString(R.string.snackbar_all_loaded)))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "load more error", e)
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
                _uiEvent.emit(MainUiEvent.ShowError(getApplication<Application>().getString(R.string.error_generic)))
            }
        }
    }

    fun unsubscribeAndClean(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean) {
        finalizePendingAction()

        val currentList = _uiState.value.subscriptions.toMutableList()
        lastRemovedSubscriptionIndex = currentList.indexOfFirst { it.senderEmail == subscription.senderEmail }
        if (lastRemovedSubscriptionIndex == -1) return

        lastRemovedSubscription = currentList.removeAt(lastRemovedSubscriptionIndex)
        _uiState.value = _uiState.value.copy(
            subscriptions = currentList,
            processingEmail = null,
            selectedItems = _uiState.value.selectedItems - subscription
        )
        applyFilter()

        pendingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _uiState.value = _uiState.value.copy(processingEmail = subscription.senderEmail)
            val action = unsubscribeAndCleanUseCase(account, subscription, cleanEmails)
            _uiState.value = _uiState.value.copy(processingEmail = null)

            if (action is UnsubscribeAction.NotFound) {
                undoLastAction(informUser = false)
                _uiEvent.emit(MainUiEvent.ShowError(getApplication<Application>().getString(R.string.error_no_unsubscribe_method)))
            } else {
                val message = if (action is UnsubscribeAction.MailTo) {
                    getApplication<Application>().getString(R.string.snackbar_unsubscribed_mailto, subscription.senderEmail)
                } else {
                    getApplication<Application>().getString(R.string.snackbar_unsubscribed_success)
                }
                _uiEvent.emit(MainUiEvent.ShowUndo(message))
                if (action is UnsubscribeAction.Http) {
                    _uiEvent.emit(MainUiEvent.OpenUrl(action.url))
                }
            }
        }

        viewModelScope.launch {
            _uiEvent.emit(MainUiEvent.ShowUndo(getApplication<Application>().getString(R.string.snackbar_unsubscribed_success)))
        }
    }

    fun keepSubscription(subscription: Subscription) {
        finalizePendingAction()

        val currentList = _uiState.value.subscriptions.toMutableList()
        lastRemovedSubscriptionIndex = currentList.indexOfFirst { it.senderEmail == subscription.senderEmail }
        if (lastRemovedSubscriptionIndex == -1) return

        lastRemovedSubscription = currentList.removeAt(lastRemovedSubscriptionIndex)
        _uiState.value = _uiState.value.copy(
            subscriptions = currentList,
            selectedItems = _uiState.value.selectedItems - subscription
        )
        applyFilter()

        pendingJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val success = keepSubscriptionUseCase(subscription)
            if (!success) {
                undoLastAction(informUser = false)
                _uiEvent.emit(MainUiEvent.ShowError("'${subscription.senderEmail}' için hata: ${getApplication<Application>().getString(R.string.error_keep_failed)}"))
            }
        }

        viewModelScope.launch {
            _uiEvent.emit(MainUiEvent.ShowUndo(getApplication<Application>().getString(R.string.snackbar_kept, subscription.senderEmail)))
        }
    }

    fun undoLastAction(informUser: Boolean = true) {
        pendingJob?.cancel()
        pendingJob = null

        lastRemovedSubscription?.let { removed ->
            if (lastRemovedSubscriptionIndex != -1) {
                val updated = _uiState.value.subscriptions.toMutableList()
                updated.add(lastRemovedSubscriptionIndex, removed)
                _uiState.value = _uiState.value.copy(subscriptions = updated.sortedByDescending { it.emailCount })
                applyFilter()
            }
        }

        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1

        if (informUser) {
            viewModelScope.launch { _uiEvent.emit(MainUiEvent.ShowMessage(getApplication<Application>().getString(R.string.snackbar_undo))) }
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
        _uiState.value = _uiState.value.copy(selectedItems = selection)
    }

    fun startSelectionMode(subscription: Subscription) {
        toggleSelection(subscription)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedItems = emptySet())
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(selectedItems = _uiState.value.filteredSubscriptions.toSet())
    }

    fun getSelectedItemsCount(): Int = _uiState.value.selectedItems.size

    fun bulkKeepSelected() {
        val itemsToKeep = _uiState.value.selectedItems
        if (itemsToKeep.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            val updated = _uiState.value.subscriptions.toMutableList()
            updated.removeAll(itemsToKeep)
            _uiState.value = _uiState.value.copy(subscriptions = updated, selectedItems = emptySet())
            applyFilter()

            itemsToKeep.forEach { if (keepSubscriptionUseCase(it)) successCount++ }
            _uiEvent.emit(MainUiEvent.ShowMessage(getApplication<Application>().getString(R.string.bulk_action_result_kept, successCount)))
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
            _uiState.value = _uiState.value.copy(subscriptions = updated, selectedItems = emptySet())
            applyFilter()

            itemsToUnsubscribe.forEach {
                val action = unsubscribeAndCleanUseCase(account, it, cleanEmails)
                if (action !is UnsubscribeAction.NotFound) successCount++
            }
            _uiEvent.emit(MainUiEvent.ShowMessage(getApplication<Application>().getString(R.string.bulk_action_result_unsubscribed, successCount)))
        }
    }

    fun resetToIdleState() {
        pendingJob?.cancel()
        pendingJob = null
        lastRemovedSubscription = null
        lastRemovedSubscriptionIndex = -1
        lastEndDate = null
        _uiState.value = MainUiState()
    }

    private fun applyFilter() {
        val state = _uiState.value
        val filtered = if (state.searchQuery.isBlank()) {
            state.subscriptions
        } else {
            state.subscriptions.filter {
                it.senderName.contains(state.searchQuery, ignoreCase = true) ||
                    it.senderEmail.contains(state.searchQuery, ignoreCase = true)
            }
        }
        _uiState.value = state.copy(filteredSubscriptions = filtered)
    }

    private fun getNextDateRange(): Pair<Calendar, Calendar> {
        val endDate = (lastEndDate?.clone() as? Calendar) ?: Calendar.getInstance()
        val startDate = endDate.clone() as Calendar
        startDate.add(Calendar.DAY_OF_YEAR, -30)
        return Pair(startDate, endDate)
    }
}
