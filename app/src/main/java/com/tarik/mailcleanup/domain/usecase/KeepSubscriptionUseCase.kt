package com.tarik.mailcleanup.domain.usecase

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository

class KeepSubscriptionUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(subscription: Subscription): DomainResult<Unit> {
        return repository.keepSubscription(subscription)
    }
}
