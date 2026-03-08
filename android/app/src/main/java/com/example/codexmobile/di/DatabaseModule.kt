package com.example.codexmobile.di

import android.content.Context
import androidx.room.Room
import com.example.codexmobile.data.CodexDatabase
import com.example.codexmobile.data.SessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): CodexDatabase =
        Room.databaseBuilder(context, CodexDatabase::class.java, "codex.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(database: CodexDatabase): SessionDao = database.sessionDao()
}
