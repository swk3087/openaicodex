package com.example.codexmobile.data

import com.example.codexmobile.domain.Session
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository @Inject constructor(
    private val sessionDao: SessionDao
) : SessionRepository {
    override fun observeSession(sessionId: String): Flow<Session?> {
        return sessionDao.observeById(sessionId).map { entity -> entity?.toDomain() }
    }

    override suspend fun getSession(sessionId: String): Session? {
        return sessionDao.findById(sessionId)?.toDomain()
    }

    override suspend fun upsert(session: Session) {
        sessionDao.upsert(
            SessionEntity(
                id = session.id,
                selectedModel = session.selectedModel,
                runtimeVersion = session.runtimeVersion,
                state = session.state.name,
                workspacePath = session.workspacePath,
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

    private fun SessionEntity.toDomain(): Session {
        return Session(
            id = id,
            selectedModel = selectedModel,
            runtimeVersion = runtimeVersion,
            state = SessionState.valueOf(state),
            workspacePath = workspacePath,
            metadata = metadata
        )
    }
}
