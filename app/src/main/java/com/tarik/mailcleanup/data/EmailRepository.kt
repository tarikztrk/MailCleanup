package com.tarik.mailcleanup.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.Base64
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.regex.Pattern
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

sealed class UnsubscribeAction {
    data class MailTo(val recipient: String, val subject: String?) : UnsubscribeAction()
    data class Http(val url: String) : UnsubscribeAction()
    object NotFound : UnsubscribeAction()
}

class EmailRepository(private val context: Context) {

    private val processedDao = AppDatabase.getDatabase(context).processedSubscriptionDao()

    suspend fun getSubscriptions(account: GoogleSignInAccount): List<Subscription> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(context, listOf(GmailScopes.GMAIL_MODIFY))
                    .setSelectedAccount(account.account)

                val gmail = Gmail.Builder(NetHttpTransport(), GsonFactory(), credential)
                    .setApplicationName("Mail Cleanup")
                    .build()

                Log.d("EmailRepository", "Geniş sorgu başlatılıyor: category:promotions newer_than:30d")
                val messageIdResponse = gmail.users().messages().list("me")
                    .setQ("category:promotions newer_than:30d")
                    .setMaxResults(500)
                    .execute()

                val messageIds = messageIdResponse.messages ?: return@withContext emptyList()
                Log.d("EmailRepository", "Potansiyel aday ${messageIds.size} e-posta bulundu.")

                val subscriptionsMap = mutableMapOf<String, Subscription>()
                val processedEmails = processedDao.getAll().associateBy { it.senderEmail }

                messageIds.chunked(20).mapIndexed { chunkIndex, chunk ->
                    async {
                        var retryCount = 0
                        val maxRetries = 3
                        
                        while (retryCount <= maxRetries) {
                            try {
                                val batch = gmail.batch()

                                val callback = object : JsonBatchCallback<Message>() {
                                    override fun onSuccess(message: Message, responseHeaders: HttpHeaders) {
                                        if (message.payload.headers.any { it.name.equals("List-Unsubscribe", ignoreCase = true) }) {
                                            val fromHeader = message.payload.headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: return
                                            val (name, email) = parseSender(fromHeader)
                                            val processedEntry = processedEmails[email]
                                            val emailDate = message.internalDate ?: 0L
                                            if (processedEntry == null || emailDate > processedEntry.processedAt) {
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
                                        Log.e("EmailRepository", "Get Subscriptions Batch - onFailure: ${e.message}")
                                    }
                                }

                                for (msg in chunk) {
                                    gmail.users().messages().get("me", msg.id).setFormat("metadata")
                                        .setFields("id,internalDate,payload/headers")
                                        .queue(batch, callback)
                                }
                                
                                batch.execute()
                                Log.d("EmailRepository", "Abonelik tarama chunk'ı (${chunkIndex + 1}/${messageIds.chunked(20).size}) tamamlandı.")
                                
                                // Başarılı olursa döngüden çık
                                break
                                
                            } catch (e: Exception) {
                                retryCount++
                                if (e.message?.contains("Too many concurrent requests") == true || 
                                    e.message?.contains("Rate limit") == true) {
                                    
                                    Log.w("EmailRepository", "Abonelik taramada rate limit hatası, ${retryCount}. deneme. 1 saniye bekleniyor...")
                                    delay(1000L * retryCount)
                                    
                                    if (retryCount > maxRetries) {
                                        Log.e("EmailRepository", "Abonelik taramada maksimum retry sayısına ulaşıldı, chunk atlanıyor: ${e.message}")
                                        break
                                    }
                                } else {
                                    Log.e("EmailRepository", "Abonelik taramada beklenmeyen hata: ${e.message}")
                                    break
                                }
                            }
                        }
                        
                        // Chunk'lar arasında kısa gecikme
                        if (chunkIndex < messageIds.chunked(20).size - 1) {
                            delay(300L)
                        }
                    }
                }.awaitAll()

                Log.d("EmailRepository", "Filtrelenmiş ve gruplanmış abonelik sayısı: ${subscriptionsMap.values.size}")
                // Haritayı listeye çevirirken emailCount'u doldur
                val resultList = subscriptionsMap.values.map {
                    it.copy(emailCount = it.messageIds.size)
                }.sortedBy { it.senderName }

                resultList // Bu listeyi döndür
            } catch (e: Exception) {
                if (e is com.google.api.client.http.HttpResponseException) {
                    Log.e("EmailRepository", "HTTP Hatası: ${e.statusCode} - ${e.content}", e)
                } else {
                    Log.e("EmailRepository", "Abonelikler alınırken genel hata", e)
                }
                emptyList()
            }
        }
    }

    suspend fun unsubscribeAndClean(
        account: GoogleSignInAccount,
        subscription: Subscription,
        cleanEmails: Boolean
    ): UnsubscribeAction {
        return withContext(Dispatchers.IO) {
            try {
                val messageId = subscription.messageIds.firstOrNull() ?: return@withContext UnsubscribeAction.NotFound

                val credential = GoogleAccountCredential.usingOAuth2(context, listOf(GmailScopes.GMAIL_MODIFY))
                    .setSelectedAccount(account.account)
                val gmail = Gmail.Builder(NetHttpTransport(), GsonFactory(), credential)
                    .setApplicationName("Mail Cleanup").build()

                val message = gmail.users().messages().get("me", messageId).setFormat("metadata").execute()
                val unsubscribeHeader = message.payload.headers.find { it.name.equals("List-Unsubscribe", ignoreCase = true) }?.value
                    ?: return@withContext UnsubscribeAction.NotFound

                val action = parseUnsubscribeHeader(unsubscribeHeader)

                if (action is UnsubscribeAction.MailTo) {
                    sendUnsubscribeEmail(gmail, account.email!!, action.recipient, action.subject)
                    Log.d("EmailRepository", "MailTo aksiyonu tetiklendi: ${action.recipient}")
                }

                if (cleanEmails) {
                    cleanEmailsFromSender(gmail, subscription.senderEmail)
                }

                val record = ProcessedSubscription(
                    senderEmail = subscription.senderEmail,
                    status = "UNSUBSCRIBED",
                    processedAt = System.currentTimeMillis()
                )
                processedDao.insert(record)
                Log.d("EmailRepository", "${subscription.senderEmail} veritabanına 'UNSUBSCRIBED' olarak eklendi.")

                action
            } catch (e: Exception) {
                Log.e("EmailRepository", "Abonelikten çıkma hatası", e)
                UnsubscribeAction.NotFound
            }
        }
    }

    suspend fun keepSubscription(subscription: Subscription) {
        return withContext(Dispatchers.IO) {
            try {
                val record = ProcessedSubscription(
                    senderEmail = subscription.senderEmail,
                    status = "WHITELISTED",
                    processedAt = System.currentTimeMillis()
                )
                processedDao.insert(record)
                Log.d("EmailRepository", "${subscription.senderEmail} veritabanına 'WHITELISTED' olarak eklendi.")
            } catch (e: Exception) {
                Log.e("EmailRepository", "Abonelik koruma hatası", e)
            }
        }
    }

    private suspend fun cleanEmailsFromSender(gmail: Gmail, senderEmail: String) {
        try {
            Log.d("EmailRepository", "$senderEmail adresinden gelen e-postalar temizleniyor...")
            val allMessageIds = mutableListOf<String>()
            var pageToken: String? = null
            
            // Tüm mesaj ID'lerini topla
            do {
                val response = gmail.users().messages().list("me")
                    .setQ("from:$senderEmail")
                    .setPageToken(pageToken)
                    .execute()

                response.messages?.forEach { allMessageIds.add(it.id) }
                pageToken = response.nextPageToken
            } while (pageToken != null)

            if (allMessageIds.isEmpty()) {
                Log.d("EmailRepository", "Temizlenecek e-posta bulunamadı.")
                return
            }

            Log.d("EmailRepository", "${allMessageIds.size} adet e-posta bulundu, çöp kutusuna taşınıyor.")

            // Batch boyutunu küçültüyoruz (100'den 10'a) ve istekler arasında gecikme ekliyoruz
            allMessageIds.chunked(10).forEachIndexed { index, chunk ->
                var retryCount = 0
                val maxRetries = 3
                
                while (retryCount <= maxRetries) {
                    try {
                        val batch = gmail.batch()
                        val callback = object : JsonBatchCallback<Message>() {
                            override fun onSuccess(message: Message?, responseHeaders: HttpHeaders?) {
                                // Başarılı silme işlemi
                            }
                            override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
                                Log.e("EmailRepository", "Toplu silme işleminde bir alt istek başarısız: ${e.message}")
                            }
                        }
                        
                        for (id in chunk) {
                            gmail.users().messages().trash("me", id).queue(batch, callback)
                        }
                        
                        batch.execute()
                        Log.d("EmailRepository", "${chunk.size} boyutlu silme chunk'ı (${index + 1}/${allMessageIds.chunked(10).size}) işlendi.")
                        
                        // Başarılı olursa döngüden çık
                        break
                        
                    } catch (e: Exception) {
                        retryCount++
                        if (e.message?.contains("Too many concurrent requests") == true || 
                            e.message?.contains("Rate limit") == true) {
                            
                            Log.w("EmailRepository", "Rate limit hatası, ${retryCount}. deneme. 2 saniye bekleniyor...")
                            delay(2000L * retryCount) // Exponential backoff
                            
                            if (retryCount > maxRetries) {
                                Log.e("EmailRepository", "Maksimum retry sayısına ulaşıldı, chunk atlanıyor: ${e.message}")
                                break
                            }
                        } else {
                            Log.e("EmailRepository", "Beklenmeyen hata: ${e.message}")
                            break
                        }
                    }
                }
                
                // Her chunk arasında kısa bir gecikme
                if (index < allMessageIds.chunked(10).size - 1) {
                    delay(500L)
                }
            }

            Log.d("EmailRepository", "Temizleme işlemi tamamlandı.")
        } catch (e: Exception) {
            Log.e("EmailRepository", "E-postalar temizlenirken hata oluştu", e)
        }
    }

    private fun parseUnsubscribeHeader(header: String): UnsubscribeAction {
        val httpRegex = "<(https?://[^>]+)>".toRegex()
        val mailtoRegex = "<mailto:([^>]+)>".toRegex()

        httpRegex.find(header)?.let { return UnsubscribeAction.Http(it.groupValues[1]) }

        mailtoRegex.find(header)?.let {
            val mailtoPart = it.groupValues[1]
            val parts = mailtoPart.split('?')
            val recipient = parts[0]
            val subject = if (parts.size > 1 && parts[1].startsWith("subject=")) {
                parts[1].substringAfter("subject=")
            } else { "Unsubscribe" }
            return UnsubscribeAction.MailTo(recipient, subject)
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
        val message = Message().setRaw(encodedEmail)

        gmail.users().messages().send("me", message).execute()
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