package com.tarik.mailcleanup.domain.usecase

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository

/**
 * Kullanıcının bir göndereni "keep/whitelist" olarak işaretlemesini yönetir.
 */
class KeepSubscriptionUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(subscription: Subscription): DomainResult<Unit> {
        return repository.keepSubscription(subscription)
    }
}
