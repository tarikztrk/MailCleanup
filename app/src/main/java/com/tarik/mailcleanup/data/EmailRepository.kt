package com.tarik.mailcleanup.data

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EmailRepository {

    // Bu bir ağ işlemi olduğu için Coroutine'ler içinde çalışmalı.
    // Hata fırlatabileceği için suspend fonksiyonu olarak tanımlıyoruz.
    suspend fun findSubscriptionEmails(context: Context, account: GoogleSignInAccount): Int {
        // withContext(Dispatchers.IO), bu bloğun arka planda bir thread'de çalışmasını sağlar.
        return withContext(Dispatchers.IO) {
            try {
                // 1. Adım: Giriş yapmış kullanıcı hesabından kimlik bilgilerini al.
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(GmailScopes.GMAIL_READONLY)
                ).setSelectedAccount(account.account)

                // 2. Adım: Gmail servisini oluştur.
                val gmail = Gmail.Builder(
                    AndroidHttp.newCompatibleTransport(),
                    GsonFactory(),
                    credential
                )
                    .setApplicationName("Unsubscribe Helper") // Proje adınız
                    .build()

                // 3. Adım: Gmail API'sine sorgu gönder.
                // "me", giriş yapmış olan kullanıcıyı temsil eder.
                // q="has:list-unsubscribe", sadece List-Unsubscribe başlığına sahip mailleri getirir.
                val response = gmail.users().messages().list("me")
                    .setQ("has:list-unsubscribe")
                    .setMaxResults(500) // Şimdilik bir limit koyalım.
                    .execute()

                val messageCount = response.messages?.size ?: 0
                Log.d("EmailRepository", "Bulunan abonelik sayısı: $messageCount")

                // Şimdilik sadece bulunan e-posta sayısını döndürelim.
                messageCount

            } catch (e: Exception) {
                Log.e("EmailRepository", "Gmail API hatası", e)
                // Hata durumunda 0 döndür veya özel bir hata fırlat.
                0
            }
        }
    }
}