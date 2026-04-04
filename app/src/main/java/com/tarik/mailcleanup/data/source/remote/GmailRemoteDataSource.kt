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
        val subscriptionsMap = mutableMapOf<String, Subscription>()

        // "Too many concurrent requests for user" hatasını azaltmak için küçük batch'ler.
        val chunks = messageIds.chunked(20)
        val maxRetries = 4

        chunks.forEachIndexed { chunkIndex, chunk ->
            var pendingIds = chunk.map { it.id }
            var retryCount = 0

            while (pendingIds.isNotEmpty() && retryCount <= maxRetries) {
                val failedIds = ConcurrentLinkedQueue<String>()
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
                    pendingIds.forEach { failedIds.add(it) }
                }

                val nextPending = failedIds.toList().distinct()
                if (nextPending.isEmpty()) break

                pendingIds = nextPending
                retryCount++
                if (retryCount <= maxRetries) {
                    // Basit exponential backoff + küçük jitter etkisi.
                    val backoff = 400L * (1L shl retryCount.coerceAtMost(5))
                    delay(backoff + (chunkIndex % 5) * 120L)
                }
            }
        }

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

        if (cleanEmails) {
            cleanEmailsFromSender(gmail, subscription.senderEmail)
        }

        action
    }

    private fun buildGmail(account: MailAccount): Gmail {
        val credential = GoogleAccountCredential.usingOAuth2(context, listOf(GmailScopes.GMAIL_MODIFY))
            .setSelectedAccount(Account(account.accountName, "com.google"))
        return Gmail.Builder(NetHttpTransport(), GsonFactory(), credential)
            .setApplicationName("Mail Cleanup")
            .build()
    }

    private suspend fun cleanEmailsFromSender(gmail: Gmail, senderEmail: String) {
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

        if (allMessageIds.isEmpty()) return

        // Silme/trash işlemlerinde daha küçük chunk, rate-limit riskini düşürür.
        val deleteChunks = allMessageIds.chunked(10)
        deleteChunks.forEachIndexed { index, chunk ->
            var retryCount = 0
            val maxRetries = 3
            while (retryCount <= maxRetries) {
                try {
                    val batch = gmail.batch()
                    val callback = object : JsonBatchCallback<Message>() {
                        override fun onSuccess(message: Message?, responseHeaders: HttpHeaders?) = Unit
                        override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) = Unit
                    }

                    for (id in chunk) {
                        gmail.users().messages().trash("me", id).queue(batch, callback)
                    }
                    batch.execute()
                    break
                } catch (e: Exception) {
                    retryCount++
                    val isRateLimit = e.message?.contains("Too many concurrent requests") == true ||
                        e.message?.contains("Rate limit") == true
                    if (isRateLimit && retryCount <= maxRetries) {
                        // Rate limit'te kontrollü bekleme.
                        delay(2000L * retryCount)
                    } else {
                        break
                    }
                }
            }
            if (index < deleteChunks.size - 1) delay(500L)
        }
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
