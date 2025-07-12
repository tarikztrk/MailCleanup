package com.tarik.mailcleanup.data
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlinx.coroutines.delay


class EmailRepository(private val context: Context) {

    // Fonksiyon artık bize özel Subscription nesnesini döndürecek.
    suspend fun getSubscriptions(account: GoogleSignInAccount): List<Subscription> {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(context, listOf(GmailScopes.GMAIL_READONLY))
                    .setSelectedAccount(account.account)

                val gmail = Gmail.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory(), credential)
                    .setApplicationName("Unsubscribe Helper")
                    .build()

                Log.d("EmailRepository", "Geniş sorgu başlatılıyor: category:promotions newer_than:30d")
                val messageIdResponse = gmail.users().messages().list("me")
                    .setQ("category:promotions newer_than:30d")
                    .setMaxResults(500)
                    .execute()

                val messageIds = messageIdResponse.messages ?: return@withContext emptyList()
                Log.d("EmailRepository", "Potansiyel aday ${messageIds.size} e-posta bulundu. Şimdi 100'lük gruplar halinde işlenecek.")

                // Tüm sonuçları toplayacağımız ortak harita.
                val subscriptionsMap = mutableMapOf<String, Subscription>()

                // YENİ MANTIK: messageIds listesini 100'lük parçalara böl.
                messageIds.chunked(30).forEach { chunk ->
                    Log.d("EmailRepository", "Yeni bir chunk işleniyor. Boyut: ${chunk.size}")

                    // Her bir 100'lük grup için yeni bir BatchRequest ve callback oluştur.
                    val batch = gmail.batch()
                    val callback = object : JsonBatchCallback<Message>() {
                        override fun onSuccess(message: Message, responseHeaders: HttpHeaders) {
                            val hasUnsubscribeHeader = message.payload.headers.any { it.name.equals("List-Unsubscribe", ignoreCase = true) }

                            if (hasUnsubscribeHeader) {
                                val fromHeader = message.payload.headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: return
                                val (name, email) = parseSender(fromHeader)

                                // Sonuçları ortak haritaya ekle.
                                val subscription = subscriptionsMap.getOrPut(email) {
                                    Subscription(senderName = name, senderEmail = email)
                                }
                                subscription.messageIds.add(message.id)
                            }
                        }

                        override fun onFailure(e: GoogleJsonError, responseHeaders: HttpHeaders) {
                            Log.e("EmailRepository", "Chunk içindeki batch request hatası: ${e.message}")
                        }
                    }

                    // Bu chunk'taki her bir ID için isteği batch'e ekle.
                    for (msg in chunk) {
                        gmail.users().messages().get("me", msg.id).setFormat("metadata").queue(batch, callback)
                    }

                    // Bu chunk'a ait batch isteğini çalıştır.
                    batch.execute()
                }

                Log.d("EmailRepository", "Tüm chunk'lar tamamlandı. Gruplanmış abonelik sayısı: ${subscriptionsMap.values.size}")
                subscriptionsMap.values.toList().sortedBy { it.senderName }

            } catch (e: Exception) {
                // BatchRequest'ten gelen HttpResponseException'ı yakalamak için logu detaylandıralım.
                if (e is com.google.api.client.http.HttpResponseException) {
                    Log.e("EmailRepository", "HTTP Hatası: ${e.statusCode} - ${e.content}", e)
                } else {
                    Log.e("EmailRepository", "Abonelikler alınırken genel hata", e)
                }
                emptyList()
            }
        }
    }

    // "Ad Soyad <email@adres.com>" formatını ayrıştıran yardımcı fonksiyon
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