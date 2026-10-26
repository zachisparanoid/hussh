package com.hussh.di

import com.hussh.data.crypto.AndroidKeystoreMasterKeyProvider
import com.hussh.data.crypto.MasterKeyProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {
    @Binds
    @Singleton
    abstract fun bindMasterKeyProvider(impl: AndroidKeystoreMasterKeyProvider): MasterKeyProvider
}
