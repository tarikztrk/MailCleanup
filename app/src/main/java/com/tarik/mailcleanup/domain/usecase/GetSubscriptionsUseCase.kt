package com.tarik.mailcleanup.domain.usecase

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository
import java.util.Calendar

class GetSubscriptionsUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(account: GoogleSignInAccount, startDate: Calendar, endDate: Calendar): DomainResult<List<Subscription>> {
        return repository.getSubscriptions(account, startDate, endDate)
    }
}
