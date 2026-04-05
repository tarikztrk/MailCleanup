package com.tarik.mailcleanup.domain.repository

import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import java.util.Calendar

/**
 * Domain'in ihtiyaç duyduğu veri kontratı.
 * Implementasyon detayı (Gmail, Room vb.) data katmanında kalır.
 */
interface SubscriptionRepository {
    suspend fun getSubscriptions(account: MailAccount, startDate: Calendar, endDate: Calendar): DomainResult<List<Subscription>>
    suspend fun unsubscribeAndClean(account: MailAccount, subscription: Subscription, cleanEmails: Boolean): DomainResult<UnsubscribeAction>
    suspend fun unsubscribeBySender(account: MailAccount, senderName: String, senderEmail: String, cleanEmails: Boolean): DomainResult<UnsubscribeAction>
    suspend fun deleteAllEmailsBySender(account: MailAccount, senderEmail: String): DomainResult<Int>
    suspend fun keepSubscription(subscription: Subscription): DomainResult<Unit>
    suspend fun deleteProcessedSubscription(email: String)
    suspend fun unkeepSubscription(subscription: Subscription)
}
