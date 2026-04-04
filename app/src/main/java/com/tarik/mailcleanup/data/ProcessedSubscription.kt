package com.tarik.mailcleanup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bir gönderen için son kullanıcı kararını yerelde saklar.
 */
@Entity(tableName = "processed_subscriptions")
data class ProcessedSubscription(
    @PrimaryKey
    val senderEmail: String,
    val status: String, // "UNSUBSCRIBED" veya "WHITELISTED"
    val processedAt: Long // Son işlem zamanı (epoch millis)
)
