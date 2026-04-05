package com.tarik.mailcleanup.data.source.remote

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.tarik.mailcleanup.data.ProcessedSubscription
import com.tarik.mailcleanup.domain.model.MailAccount
import com.tarik.mailcleanup.domain.model.Subscription
import com.tarik.mailcleanup.domain.model.UnsubscribeAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import javax.inject.Inject
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Gmail API ile konuşan remote data source.
 * Abonelik tespiti, unsubscribe header parsing ve opsiyonel temizleme burada yapılır.
 */
class GmailRemoteDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private data class AdaptiveThrottle(
        val minBatchSize: Int,
        val maxBatchSize: Int,
        val initialBatchSize: Int,
        val minInterBatchDelayMs: Long,
        val maxInterBatchDelayMs: Long,
        val initialInterBatchDelayMs: Long
    ) {
        var batchSize: Int = initialBatchSize
            private set
        var interBatchDelayMs: Long = initialInterBatchDelayMs
            private set
        private var successStreak: Int = 0

        fun onAttemptResult(totalRequests: Int, failedRequests: Int, rateLimitFailures: Int) {
            if (totalRequests <= 0) return

            val failureRatio = failedRequests.toDouble() / totalRequests.toDouble()
            val rateLimited = rateLimitFailures > 0

            when {
                rateLimited || failureRatio >= 0.25 -> {
                    // Hata döneminde hız düşür: daha küçük batch + daha fazla bekleme.
                    batchSize = (batchSize * 0.7).toInt().coerceIn(minBatchSize, maxBatchSize)
                    interBatchDelayMs = (interBatchDelayMs + 220L).coerceIn(minInterBatchDelayMs, maxInterBatchDelayMs)
                    successStreak = 0
                }
                failedRequests == 0 -> {
                    // Arka arkaya başarı varsa temkinli şekilde hızı geri artır.
                    successStreak++
                    if (successStreak >= 3) {
                        batchSize = (batchSize + 2).coerceIn(minBatchSize, maxBatchSize)
                        interBatchDelayMs = (interBatchDelayMs - 80L).coerceIn(minInterBatchDelayMs, maxInterBatchDelayMs)
                        successStreak = 0
                    }
                }
                else -> {
                    successStreak = 0
                }
            }
        }

        fun retryDelayMs(retryCount: Int, rateLimited: Boolean): Long {
            val exponential = 350L * (1L shl retryCount.coerceAtMost(5))
            val penalty = if (rateLimited) interBatchDelayMs else interBatchDelayMs / 2
            return (exponential + penalty).coerceAtMost(maxInterBatchDelayMs + 2000L)
        }
    }

    private fun isRateLimitMessage(message: String?): Boolean {
        val normalized = message?.lowercase().orEmpty()
        return normalized.contains("too many concurrent requests") ||
            normalized.contains("rate limit") ||
            normalized.contains("quota") ||
            normalized.contains("userrate") ||
            normalized.contains("429")
    }

    private fun isRateLimitError(error: GoogleJsonError): Boolean {
        val reasons = error.errors?.joinToString(" ") { it.reason ?: "" }.orEmpty()
        return isRateLimitMessage("${error.message} $reasons")
    }

    suspend fun fetchSubscriptions(
        account: MailAccount,
        startDate: Calendar,
        endDate: Calendar,
        processedByEmail: Map<String, ProcessedSubscription>
    ): List<Subscription> = withContext(Dispatchers.IO) {
        // Tüm Gmail çağrıları IO dispatcher üzerinde çalıştırılır.
        val gmail = buildGmail(account)

        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        val query = "category:promotions after:${dateFormat.format(startDate.time)} before:${dateFormat.format(endDate.time)}"
        Log.d("GmailRemoteDataSource", "Sorgu başlatılıyor: $query")

        val messageIdResponse = gmail.users().messages().list("me")
            .setQ(query)
            .setMaxResults(500)
            .execute()

        val messageIds = messageIdResponse.messages ?: return@withContext emptyList()
        val allMessageIds = messageIds.map { it.id }
        val subscriptionsMap = mutableMapOf<String, Subscription>()

        // Runtime sinyaline göre batch boyutu/bekleme ayarlayan throttle.
        val throttle = AdaptiveThrottle(
            minBatchSize = 4,
            maxBatchSize = 24,
            initialBatchSize = 12,
            minInterBatchDelayMs = 70L,
            maxInterBatchDelayMs = 2800L,
            initialInterBatchDelayMs = 180L
        )
        val maxRetries = 4
        var cursor = 0

        while (cursor < allMessageIds.size) {
            val chunkSize = throttle.batchSize
            val endExclusive = (cursor + chunkSize).coerceAtMost(allMessageIds.size)
            var pendingIds: List<String> = allMessageIds.subList(cursor, endExclusive)
            var retryCount = 0
            cursor = endExclusive

            while (pendingIds.isNotEmpty() && retryCount <= maxRetries) {
                val failedIds = ConcurrentLinkedQueue<String>()
                val rateLimitFailures = AtomicInteger(0)
                try {
                    // Her chunk için tek batch request: ağ maliyetini azaltır.
                    val batch = gmail.batch()

                    pendingIds.forEach { messageId ->
                        val callback = object : JsonBatchCallback<Message>() {
                            override fun onSuccess(message: Message, responseHeaders: HttpHeaders) {
                                if (message.payload.headers.any { it.name.equals("List-Unsubscribe", ignoreCase = true) }) {
                                    val fromHeader = message.payload.headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: return
                                    val (name, email) = parseSender(fromHeader)
                                    val processedEntry = processedByEmail[email]
                                    val emailDate = message.internalDate ?: 0L
                                    if (processedEntry == null || emailDate > processedEntry.processedAt) {
                                        // Aynı gönderen için tek kayıt biriktirip message id'leri altında topluyoruz.
                                        synchronized(subscriptionsMap) {
                                            val subscription = subscriptionsMap.getOrPut(email) {
                                                Subscription(senderName = name, senderEmail = email)
                                            }
                                            subscription.messageIds.add(message.id)
                                        }
                                    }
                                }
                            }

                            override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
                                failedIds.add(messageId)
                                if (isRateLimitError(e)) {
                                    rateLimitFailures.incrementAndGet()
                                }
                                Log.e("GmailRemoteDataSource", "Batch fetch onFailure: ${e.message}")
                            }
                        }

                        gmail.users().messages().get("me", messageId).setFormat("metadata")
                            .setFields("id,internalDate,payload/headers")
                            .queue(batch, callback)
                    }

                    batch.execute()
                } catch (e: Exception) {
                    Log.e("GmailRemoteDataSource", "Batch execute error: ${e.message}", e)
                    if (isRateLimitMessage(e.message)) {
                        rateLimitFailures.incrementAndGet()
                    }
                    pendingIds.forEach { failedIds.add(it) }
                }

                val nextPending = failedIds.toList().distinct()
                throttle.onAttemptResult(
                    totalRequests = pendingIds.size,
                    failedRequests = nextPending.size,
                    rateLimitFailures = rateLimitFailures.get()
                )

                if (nextPending.isEmpty()) break

                pendingIds = nextPending
                retryCount++
                if (retryCount <= maxRetries) {
                    delay(throttle.retryDelayMs(retryCount, rateLimitFailures.get() > 0))
                }
            }

            if (cursor < allMessageIds.size) {
                delay(throttle.interBatchDelayMs)
            }
        }

        Log.d(
            "GmailRemoteDataSource",
            "Fetch tamamlandi. ToplamId=${allMessageIds.size}, batchSize=${throttle.batchSize}, interDelayMs=${throttle.interBatchDelayMs}"
        )
        subscriptionsMap.values.map { it.copy(emailCount = it.messageIds.size) }
    }

    suspend fun unsubscribeAndClean(
        account: MailAccount,
        subscription: Subscription,
        cleanEmails: Boolean
    ): UnsubscribeAction? = withContext(Dispatchers.IO) {
        val messageId = subscription.messageIds.firstOrNull() ?: return@withContext null
        val gmail = buildGmail(account)

        val message = gmail.users().messages().get("me", messageId).setFormat("metadata").execute()
        val unsubscribeHeader = message.payload.headers
            .find { it.name.equals("List-Unsubscribe", ignoreCase = true) }
            ?.value ?: return@withContext null

        val action = parseUnsubscribeHeader(unsubscribeHeader)
        if (action is UnsubscribeAction.NotFound) return@withContext null

        if (action is UnsubscribeAction.MailTo) {
            sendUnsubscribeEmail(gmail, account.email.orEmpty(), action.recipient, action.subject)
        }

        if (cleanEmails) cleanEmailsFromSender(gmail, subscription.senderEmail)

        action
    }

    suspend fun unsubscribeBySender(
        account: MailAccount,
        senderEmail: String,
        cleanEmails: Boolean
    ): UnsubscribeAction? = withContext(Dispatchers.IO) {
        val gmail = buildGmail(account)
        val listResponse = gmail.users().messages().list("me")
            .setQ("from:$senderEmail")
            .setMaxResults(20)
            .execute()

        val ids = listResponse.messages?.map { it.id }.orEmpty()
        for (id in ids) {
            val message = gmail.users().messages().get("me", id).setFormat("metadata").execute()
            val unsubscribeHeader = message.payload.headers
                .find { it.name.equals("List-Unsubscribe", ignoreCase = true) }
                ?.value
                ?: continue

            val action = parseUnsubscribeHeader(unsubscribeHeader)
            if (action is UnsubscribeAction.NotFound) continue

            if (action is UnsubscribeAction.MailTo) {
                sendUnsubscribeEmail(gmail, account.email.orEmpty(), action.recipient, action.subject)
            }
            if (cleanEmails) cleanEmailsFromSender(gmail, senderEmail)
            return@withContext action
        }
        null
    }

    suspend fun deleteAllEmailsBySender(account: MailAccount, senderEmail: String): Int = withContext(Dispatchers.IO) {
        val gmail = buildGmail(account)
        cleanEmailsFromSender(gmail, senderEmail)
    }

    private fun buildGmail(account: MailAccount): Gmail {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(GmailScopes.GMAIL_MODIFY))
            .setSelectedAccount(Account(account.accountName, "com.google"))
        return Gmail.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("Mail Cleanup")
            .build()
    }

    private suspend fun cleanEmailsFromSender(gmail: Gmail, senderEmail: String): Int {
        // Gönderene ait tüm mesaj id'lerini sayfalar halinde topluyoruz.
        val allMessageIds = mutableListOf<String>()
        var pageToken: String? = null
        do {
            val response = gmail.users().messages().list("me")
                .setQ("from:$senderEmail")
                .setPageToken(pageToken)
                .execute()
            response.messages?.forEach { allMessageIds.add(it.id) }
            pageToken = response.nextPageToken
        } while (pageToken != null)

        if (allMessageIds.isEmpty()) return 0

        val throttle = AdaptiveThrottle(
            minBatchSize = 3,
            maxBatchSize = 12,
            initialBatchSize = 8,
            minInterBatchDelayMs = 120L,
            maxInterBatchDelayMs = 3000L,
            initialInterBatchDelayMs = 320L
        )
        var deletedCount = 0
        var cursor = 0

        while (cursor < allMessageIds.size) {
            val endExclusive = (cursor + throttle.batchSize).coerceAtMost(allMessageIds.size)
            var pendingIds: List<String> = allMessageIds.subList(cursor, endExclusive)
            cursor = endExclusive
            var retryCount = 0
            val maxRetries = 3
            while (pendingIds.isNotEmpty() && retryCount <= maxRetries) {
                val failedIds = ConcurrentLinkedQueue<String>()
                val rateLimitFailures = AtomicInteger(0)
                try {
                    val batch = gmail.batch()

                    for (id in pendingIds) {
                        val callback = object : JsonBatchCallback<Message>() {
                            override fun onSuccess(message: Message?, responseHeaders: HttpHeaders?) {
                                deletedCount++
                            }
                            override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
                                failedIds.add(id)
                                if (isRateLimitError(e)) {
                                    rateLimitFailures.incrementAndGet()
                                }
                                Log.e("GmailRemoteDataSource", "Batch delete onFailure: ${e.message}")
                            }
                        }
                        gmail.users().messages().trash("me", id).queue(batch, callback)
                    }
                    batch.execute()
                } catch (e: Exception) {
                    if (isRateLimitMessage(e.message)) {
                        rateLimitFailures.incrementAndGet()
                    }
                    Log.e("GmailRemoteDataSource", "Batch delete execute error: ${e.message}", e)
                    pendingIds.forEach { failedIds.add(it) }
                }

                val nextPending = failedIds.toList().distinct()
                throttle.onAttemptResult(
                    totalRequests = pendingIds.size,
                    failedRequests = nextPending.size,
                    rateLimitFailures = rateLimitFailures.get()
                )
                if (nextPending.isEmpty()) break

                pendingIds = nextPending
                retryCount++
                if (retryCount <= maxRetries) {
                    delay(throttle.retryDelayMs(retryCount, rateLimitFailures.get() > 0))
                }
            }

            if (cursor < allMessageIds.size) {
                delay(throttle.interBatchDelayMs)
            }
        }
        return deletedCount
    }

    private fun parseUnsubscribeHeader(header: String): UnsubscribeAction {
        // RFC'deki yaygın iki formu destekliyoruz: <mailto:...> ve <https://...>
        val httpRegex = "<(https?://[^>]+)>".toRegex()
        val mailtoRegex = "<mailto:([^>]+)>".toRegex()

        mailtoRegex.find(header)?.let {
            val mailtoPart = it.groupValues[1]
            val parts = mailtoPart.split('?')
            val recipient = parts[0]
            val subject = if (parts.size > 1 && parts[1].startsWith("subject=")) {
                parts[1].substringAfter("subject=")
            } else {
                "Unsubscribe"
            }
            return UnsubscribeAction.MailTo(recipient, subject)
        }

        httpRegex.find(header)?.let {
            return UnsubscribeAction.Http(it.groupValues[1])
        }

        return UnsubscribeAction.NotFound
    }

    private fun sendUnsubscribeEmail(gmail: Gmail, from: String, to: String, subject: String?) {
        val props = Properties()
        val session = Session.getDefaultInstance(props, null)
        val email = MimeMessage(session)
        email.setFrom(InternetAddress(from))
        email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
        email.subject = subject ?: "Unsubscribe"
        email.setText("")

        val buffer = ByteArrayOutputStream()
        email.writeTo(buffer)
        val rawMessageBytes = buffer.toByteArray()
        val encodedEmail = Base64.encodeBase64URLSafeString(rawMessageBytes)
        val messageToSend = Message().setRaw(encodedEmail)

        val sentMessage = gmail.users().messages().send("me", messageToSend).execute()
        try {
            gmail.users().messages().trash("me", sentMessage.id).execute()
        } catch (_: Exception) {
        }
    }

    private fun parseSender(fromHeader: String): Pair<String, String> {
        val pattern = Pattern.compile("(.*)<(.*)>")
        val matcher = pattern.matcher(fromHeader)
        return if (matcher.find()) {
            val name = matcher.group(1)?.trim()?.replace("\"", "") ?: "Bilinmeyen Gönderici"
            val email = matcher.group(2)?.trim() ?: fromHeader
            Pair(if (name.isEmpty()) email else name, email)
        } else {
            Pair(fromHeader, fromHeader)
        }
    }
}
