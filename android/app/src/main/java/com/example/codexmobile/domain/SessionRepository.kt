package com.example.codexmobile.domain

import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSession(sessionId: String): Flow<Session>
    suspend fun updateModel(sessionId: String, model: String)
    suspend fun updateState(sessionId: String, state: SessionState)
}
