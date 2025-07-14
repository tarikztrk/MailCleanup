package com.tarik.mailcleanup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_subscriptions")
data class ProcessedSubscription(
    @PrimaryKey
    val senderEmail: String,
    val status: String, // "UNSUBSCRIBED" veya "WHITELISTED" gibi durumlar için
    val processedAt: Long // İşlemin yapıldığı zaman damgası (timestamp)
)