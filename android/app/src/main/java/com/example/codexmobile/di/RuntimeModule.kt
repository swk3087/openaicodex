package com.example.codexmobile.di

import com.example.codexmobile.runtime.RuntimeManager
import com.example.codexmobile.runtime.RuntimeManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {
    @Binds
    @Singleton
    abstract fun bindRuntimeManager(runtimeManagerImpl: RuntimeManagerImpl): RuntimeManager
}
