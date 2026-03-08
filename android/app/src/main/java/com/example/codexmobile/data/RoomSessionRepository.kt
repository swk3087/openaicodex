package com.example.codexmobile.data

import com.example.codexmobile.domain.Session
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository(
    private val sessionDao: SessionDao
) : SessionRepository {
    override fun observeSession(sessionId: String): Flow<Session> {
        return sessionDao.observeById(sessionId).map { entity ->
            Session(
                id = entity.id,
                selectedModel = entity.selectedModel,
                runtimeVersion = entity.runtimeVersion,
                state = SessionState.valueOf(entity.state),
                workspaceUri = entity.workspaceUri
            )
        }
    }

    override suspend fun updateModel(sessionId: String, model: String) {
        sessionDao.updateModel(sessionId, model)
    }

    override suspend fun updateState(sessionId: String, state: SessionState) {
        sessionDao.updateState(sessionId, state.name)
    }
}
