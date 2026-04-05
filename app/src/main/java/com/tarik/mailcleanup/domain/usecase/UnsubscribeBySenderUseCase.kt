package com.tarik.mailcleanup.domain.usecase

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository

class UnsubscribeBySenderUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(
        account: MailAccount,
        senderName: String,
        senderEmail: String,
        cleanEmails: Boolean
    ): DomainResult<UnsubscribeAction> {
        return repository.unsubscribeBySender(account, senderName, senderEmail, cleanEmails)
    }
}
