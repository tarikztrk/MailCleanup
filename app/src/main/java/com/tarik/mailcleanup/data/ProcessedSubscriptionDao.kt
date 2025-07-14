package com.tarik.mailcleanup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProcessedSubscriptionDao {
    // Bir kaydı ekler veya varsa günceller.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: ProcessedSubscription)

    // Verilen e-posta adresine sahip kaydı getirir.
    @Query("SELECT * FROM processed_subscriptions WHERE senderEmail = :email")
    suspend fun getByEmail(email: String): ProcessedSubscription?

    // Tüm işlenmiş kayıtları getirir (hata ayıklama için kullanışlı).
    @Query("SELECT * FROM processed_subscriptions")
    suspend fun getAll(): List<ProcessedSubscription>
}