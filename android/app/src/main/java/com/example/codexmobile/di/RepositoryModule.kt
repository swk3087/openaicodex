package com.example.codexmobile.di

import com.example.codexmobile.data.RoomSessionRepository
import com.example.codexmobile.domain.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSessionRepository(repository: RoomSessionRepository): SessionRepository
}
