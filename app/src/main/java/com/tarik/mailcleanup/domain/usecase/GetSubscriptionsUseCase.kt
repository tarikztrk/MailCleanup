package com.tarik.mailcleanup.domain.usecase

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository
import java.util.Calendar

/**
 * Verilen tarih aralığındaki abonelikleri getirir.
 */
class GetSubscriptionsUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(account: MailAccount, startDate: Calendar, endDate: Calendar): DomainResult<List<Subscription>> {
        return repository.getSubscriptions(account, startDate, endDate)
    }
}
