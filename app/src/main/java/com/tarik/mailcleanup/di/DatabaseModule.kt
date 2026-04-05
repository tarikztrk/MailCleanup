package com.tarik.mailcleanup.di

import android.content.Context
import androidx.room.Room
import com.tarik.mailcleanup.data.AppDatabase
import com.tarik.mailcleanup.data.ProcessedSubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mail_cleanup_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideProcessedSubscriptionDao(
        database: AppDatabase
    ): ProcessedSubscriptionDao = database.processedSubscriptionDao()
}
