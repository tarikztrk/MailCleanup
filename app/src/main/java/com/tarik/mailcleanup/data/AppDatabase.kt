package com.tarik.mailcleanup.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProcessedSubscription::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun processedSubscriptionDao(): ProcessedSubscriptionDao
}
