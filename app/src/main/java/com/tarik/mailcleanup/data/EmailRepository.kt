package com.tarik.mailcleanup.data

import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.data.source.local.ProcessedSubscriptionLocalDataSource
import com.tarik.mailcleanup.data.source.remote.GmailRemoteDataSource
import com.tarik.mailcleanup.domain.model.DomainError
import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository
import java.util.Calendar
import javax.inject.Inject

class EmailRepository @Inject constructor(
    private val remoteDataSource: GmailRemoteDataSource,
    private val localDataSource: ProcessedSubscriptionLocalDataSource
) : SubscriptionRepository {

    override suspend fun getSubscriptions(
        account: GoogleSignInAccount,
        startDate: Calendar,
        endDate: Calendar
    ): DomainResult<List<Subscription>> {
        return try {
            val processed = localDataSource.getProcessedMapByEmail()
            DomainResult.Success(remoteDataSource.fetchSubscriptions(account, startDate, endDate, processed))
        } catch (e: Exception) {
            Log.e("EmailRepository", "Abonelikler alınırken genel hata", e)
            DomainResult.Error(DomainError.Generic)
        }
    }

    override suspend fun unsubscribeAndClean(
        account: GoogleSignInAccount,
        subscription: Subscription,
        cleanEmails: Boolean
    ): DomainResult<UnsubscribeAction> {
        return try {
            val action = remoteDataSource.unsubscribeAndClean(account, subscription, cleanEmails)
                ?: return DomainResult.Error(DomainError.NoUnsubscribeMethod)

            localDataSource.markUnsubscribed(subscription.senderEmail)
            DomainResult.Success(action)
        } catch (e: Exception) {
            Log.e("EmailRepository", "Abonelikten çıkma hatası", e)
            DomainResult.Error(DomainError.Generic)
        }
    }

    override suspend fun keepSubscription(subscription: Subscription): DomainResult<Unit> {
        return try {
            localDataSource.markWhitelisted(subscription.senderEmail)
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("EmailRepository", "Abonelik koruma hatası", e)
            DomainResult.Error(DomainError.KeepFailed)
        }
    }

    override suspend fun deleteProcessedSubscription(email: String) {
        localDataSource.deleteByEmail(email)
    }

    override suspend fun unkeepSubscription(subscription: Subscription) {
        localDataSource.deleteByEmail(subscription.senderEmail)
    }
}
