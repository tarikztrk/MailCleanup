package com.tarik.mailcleanup.data.source.local

import android.content.Context
import com.tarik.mailcleanup.data.AppDatabase
import com.tarik.mailcleanup.data.ProcessedSubscription
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Room üzerinde çalışan local data source.
 * Repository bu sınıfı kullanarak local state'i yönetir.
 */
class ProcessedSubscriptionLocalDataSource @Inject constructor(
    @ApplicationContext context: Context
) {
    private val processedDao = AppDatabase.getDatabase(context).processedSubscriptionDao()

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
