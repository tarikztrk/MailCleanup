package com.tarik.mailcleanup.data

import android.util.Log
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.tarik.mailcleanup.data.source.local.ProcessedSubscriptionLocalDataSource
import com.tarik.mailcleanup.data.source.remote.GmailRemoteDataSource
import com.tarik.mailcleanup.domain.model.DomainError
import com.tarik.mailcleanup.domain.model.DomainResult
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Calendar
import javax.inject.Inject

class EmailRepository @Inject constructor(
    private val remoteDataSource: GmailRemoteDataSource,
    private val localDataSource: ProcessedSubscriptionLocalDataSource
) : SubscriptionRepository {

    override suspend fun getSubscriptions(
        account: MailAccount,
        startDate: Calendar,
        endDate: Calendar
    ): DomainResult<List<Subscription>> {
        return try {
            val processed = localDataSource.getProcessedMapByEmail()
            DomainResult.Success(remoteDataSource.fetchSubscriptions(account, startDate, endDate, processed))
        } catch (e: Exception) {
            Log.e("EmailRepository", "Abonelikler alınırken genel hata", e)
            DomainResult.Error(classifyError(e))
        }
    }

    override suspend fun unsubscribeAndClean(
        account: MailAccount,
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
            DomainResult.Error(classifyError(e))
        }
    }

    override suspend fun keepSubscription(subscription: Subscription): DomainResult<Unit> {
        return try {
            localDataSource.markWhitelisted(subscription.senderEmail)
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            Log.e("EmailRepository", "Abonelik koruma hatası", e)
            DomainResult.Error(classifyError(e, fallback = DomainError.KeepFailed))
        }
    }

    override suspend fun deleteProcessedSubscription(email: String) {
        localDataSource.deleteByEmail(email)
    }

    override suspend fun unkeepSubscription(subscription: Subscription) {
        localDataSource.deleteByEmail(subscription.senderEmail)
    }

    private fun classifyError(
        throwable: Throwable,
        fallback: DomainError = DomainError.Generic
    ): DomainError {
        val message = throwable.message?.lowercase().orEmpty()

        if (
            message.contains("too many concurrent requests") ||
            message.contains("rate limit") ||
            message.contains("quota exceeded") ||
            message.contains("userrate") ||
            message.contains("429")
        ) {
            return DomainError.RateLimit
        }

        if (throwable is GoogleJsonResponseException) {
            return when (throwable.statusCode) {
                401 -> DomainError.Auth
                403 -> if (message.contains("rate limit") || message.contains("quota")) {
                    DomainError.RateLimit
                } else {
                    DomainError.Auth
                }
                429 -> DomainError.RateLimit
                in 500..599 -> DomainError.Server
                else -> fallback
            }
        }

        if (
            message.contains("unauthorized") ||
            message.contains("invalid credentials") ||
            message.contains("invalid_grant") ||
            message.contains("auth")
        ) {
            return DomainError.Auth
        }

        if (
            throwable is UnknownHostException ||
            throwable is SocketTimeoutException ||
            throwable is ConnectException ||
            throwable is IOException
        ) {
            return DomainError.Network
        }

        if (
            message.contains("500") ||
            message.contains("502") ||
            message.contains("503") ||
            message.contains("504") ||
            message.contains("internal error")
        ) {
            return DomainError.Server
        }

        return fallback
    }
}
