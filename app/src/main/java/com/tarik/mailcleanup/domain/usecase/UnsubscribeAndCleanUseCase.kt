package com.tarik.mailcleanup.domain.usecase

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository

/**
 * Abonelikten çıkma ve opsiyonel mail temizliği akışını tek çağrıda toplar.
 */
class UnsubscribeAndCleanUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(account: MailAccount, subscription: Subscription, cleanEmails: Boolean): DomainResult<UnsubscribeAction> {
        return repository.unsubscribeAndClean(account, subscription, cleanEmails)
    }
}
