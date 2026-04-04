package com.tarik.mailcleanup.di

import com.tarik.mailcleanup.data.EmailRepository
import com.tarik.mailcleanup.domain.repository.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository interface -> implementation eşleşmeleri.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(
        repository: EmailRepository
    ): SubscriptionRepository
}
