package com.example.codexmobile.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommandGateModule {
    @Provides
    @Singleton
    fun provideAllowedCommands(): Set<String> {
        return setOf("node", "npm", "npx codex")
    }
}
