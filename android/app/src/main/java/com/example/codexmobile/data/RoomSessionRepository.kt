package com.example.codexmobile.data

import com.example.codexmobile.domain.Session
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomSessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {
    override fun observeSession(sessionId: String): Flow<Session> {
        return sessionDao.observeById(sessionId).map { entity ->
            Session(
                id = entity.id,
                selectedModel = entity.selectedModel,
                runtimeVersion = entity.runtimeVersion,
                state = SessionState.valueOf(entity.state),
                workspaceUri = entity.workspaceUri,
                metadata = entity.metadata
            )
        }
    }

    override suspend fun upsert(session: Session) {
        sessionDao.upsert(
            SessionEntity(
                id = session.id,
                selectedModel = session.selectedModel,
                runtimeVersion = session.runtimeVersion,
                state = session.state.name,
                workspaceUri = session.workspaceUri,
                metadata = session.metadata
            )
        )
    }

    override suspend fun updateModel(sessionId: String, model: String) {
        sessionDao.updateModel(sessionId, model)
    }

    override suspend fun updateState(sessionId: String, state: SessionState) {
        sessionDao.updateState(sessionId, state.name)
    }

    override suspend fun updateRuntimeVersion(sessionId: String, runtimeVersion: String) {
        sessionDao.updateRuntimeVersion(sessionId, runtimeVersion)
    }

    override suspend fun updateMetadata(sessionId: String, metadata: String) {
        sessionDao.updateMetadata(sessionId, metadata)
    }
}
