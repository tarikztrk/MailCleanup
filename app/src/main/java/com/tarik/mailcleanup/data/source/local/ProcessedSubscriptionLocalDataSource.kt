package com.tarik.mailcleanup.data.source.local

import com.tarik.mailcleanup.data.ProcessedSubscription
import com.tarik.mailcleanup.data.ProcessedSubscriptionDao
import javax.inject.Inject

/**
 * Room üzerinde çalışan local data source.
 * Repository bu sınıfı kullanarak local state'i yönetir.
 */
class ProcessedSubscriptionLocalDataSource @Inject constructor(
    private val processedDao: ProcessedSubscriptionDao
) {
    suspend fun getProcessedMapByEmail(): Map<String, ProcessedSubscription> {
        return processedDao.getAll().associateBy { it.senderEmail }
    }

    suspend fun markUnsubscribed(senderEmail: String) {
        processedDao.insert(
            ProcessedSubscription(
                senderEmail = senderEmail,
                status = "UNSUBSCRIBED",
                processedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markWhitelisted(senderEmail: String) {
        processedDao.insert(
            ProcessedSubscription(
                senderEmail = senderEmail,
                status = "WHITELISTED",
                processedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteByEmail(senderEmail: String) {
        processedDao.deleteByEmail(senderEmail)
    }
}
