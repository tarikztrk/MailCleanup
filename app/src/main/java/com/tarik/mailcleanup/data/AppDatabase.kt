package com.tarik.mailcleanup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProcessedSubscription::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun processedSubscriptionDao(): ProcessedSubscriptionDao

    companion object {
        // Volatile, bu değişkenin her zaman güncel kalmasını sağlar.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // Eğer bir instance varsa onu döndür, yoksa oluştur.
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