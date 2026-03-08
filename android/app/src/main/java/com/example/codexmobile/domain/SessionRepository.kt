package com.example.codexmobile.domain

import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    fun observeSession(sessionId: String): Flow<Session?>
    suspend fun getSession(sessionId: String): Session?
    suspend fun createIfAbsent(sessionId: String, model: String, workspaceUri: String)
    suspend fun upsert(session: Session)
    suspend fun updateModel(sessionId: String, model: String)
    suspend fun updateState(sessionId: String, state: SessionState)
    suspend fun updateRuntimeVersion(sessionId: String, runtime: String)
    suspend fun updateError(sessionId: String, code: String?, message: String?)
    suspend fun updateMetadata(sessionId: String, metadata: String)
}
