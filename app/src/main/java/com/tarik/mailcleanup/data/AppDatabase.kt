package com.tarik.mailcleanup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Uygulamanın yerel Room veritabanı.
 * Şu an sadece işlenen abonelikleri (unsubscribe/keep) saklıyor.
 */
@Database(entities = [ProcessedSubscription::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun processedSubscriptionDao(): ProcessedSubscriptionDao

    companion object {
        // Singleton instance'ın thread-safe ve güncel görünmesi için volatile tutulur.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Uygulama boyunca tek veritabanı nesnesi kullanılır.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mail_cleanup_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
