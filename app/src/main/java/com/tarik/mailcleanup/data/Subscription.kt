package com.tarik.mailcleanup.data

data class Subscription(
    val senderName: String, // Gönderenin adı (örn: "Trendyol")
    val senderEmail: String, // Gönderenin e-posta adresi
    val messageIds: MutableList<String> = mutableListOf() // Bu göndericiden gelen tüm e-posta ID'leri
)