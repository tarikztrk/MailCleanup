package com.tarik.mailcleanup.di

import com.tarik.mailcleanup.core.text.AndroidStringProvider
import com.tarik.mailcleanup.core.text.StringProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Core katmanındaki soyutlamaların Hilt bağları.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindStringProvider(
        impl: AndroidStringProvider
    ): StringProvider
}
