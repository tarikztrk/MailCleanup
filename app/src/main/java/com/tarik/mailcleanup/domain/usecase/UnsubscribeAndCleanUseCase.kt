package com.tarik.mailcleanup.domain.usecase

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository

class UnsubscribeAndCleanUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean): UnsubscribeAction {
        return repository.unsubscribeAndClean(account, subscription, cleanEmails)
    }
}
