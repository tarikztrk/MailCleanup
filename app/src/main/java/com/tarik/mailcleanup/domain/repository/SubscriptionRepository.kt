package com.tarik.mailcleanup.domain.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import java.util.Calendar

interface SubscriptionRepository {
    suspend fun getSubscriptions(account: GoogleSignInAccount, startDate: Calendar, endDate: Calendar): List<Subscription>
    suspend fun unsubscribeAndClean(account: GoogleSignInAccount, subscription: Subscription, cleanEmails: Boolean): UnsubscribeAction
    suspend fun keepSubscription(subscription: Subscription): Boolean
    suspend fun deleteProcessedSubscription(email: String)
    suspend fun unkeepSubscription(subscription: Subscription)
}
