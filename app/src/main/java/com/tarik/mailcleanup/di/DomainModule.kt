package com.tarik.mailcleanup.di

import com.tarik.mailcleanup.domain.repository.SubscriptionRepository
import com.tarik.mailcleanup.domain.usecase.GetSubscriptionsUseCase
import com.tarik.mailcleanup.domain.usecase.KeepSubscriptionUseCase
import com.tarik.mailcleanup.domain.usecase.UnsubscribeAndCleanUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Use-case nesnelerini repository bağımlılığı ile üretir.
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideGetSubscriptionsUseCase(
        repository: SubscriptionRepository
    ): GetSubscriptionsUseCase = GetSubscriptionsUseCase(repository)

    @Provides
    @Singleton
    fun provideUnsubscribeAndCleanUseCase(
        repository: SubscriptionRepository
    ): UnsubscribeAndCleanUseCase = UnsubscribeAndCleanUseCase(repository)

    @Provides
    @Singleton
    fun provideKeepSubscriptionUseCase(
        repository: SubscriptionRepository
    ): KeepSubscriptionUseCase = KeepSubscriptionUseCase(repository)
}
