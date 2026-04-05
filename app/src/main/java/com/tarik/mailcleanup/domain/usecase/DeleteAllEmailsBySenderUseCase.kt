package com.tarik.mailcleanup.domain.usecase

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository

class DeleteAllEmailsBySenderUseCase(private val repository: SubscriptionRepository) {
    suspend operator fun invoke(account: MailAccount, senderEmail: String): DomainResult<Int> {
        return repository.deleteAllEmailsBySender(account, senderEmail)
    }
}
