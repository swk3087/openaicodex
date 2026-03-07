package com.example.codexmobile.domain.usecase

import com.example.codexmobile.domain.Session
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import com.example.codexmobile.runtime.RuntimeManager
import com.example.codexmobile.runtime.RuntimeStatus
import javax.inject.Inject

class InitializeSessionRuntimeUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val runtimeManager: RuntimeManager,
    private val prepareRuntimeStateTransition: PrepareRuntimeStateTransitionUseCase,
    private val completeRuntimeStateTransition: CompleteRuntimeStateTransitionUseCase
) {
    suspend operator fun invoke(sessionId: String) {
        ensureSessionExists(sessionId)

        prepareRuntimeStateTransition(sessionId)
        val result = runtimeManager.prepareRuntime()

        sessionRepository.updateRuntimeVersion(sessionId, result.runtimeVersion)
        val errorCode = result.errorCode.orEmpty()
        sessionRepository.updateMetadata(sessionId, "runtimeErrorCode=$errorCode")

        completeRuntimeStateTransition(sessionId, result.status == RuntimeStatus.READY)
    }

    private suspend fun ensureSessionExists(sessionId: String) {
        sessionRepository.upsert(
            Session(
                id = sessionId,
                selectedModel = "gpt-5.2-codex",
                runtimeVersion = "-",
                state = SessionState.IDLE,
                workspaceUri = "/workspace",
                metadata = "runtimeErrorCode="
            )
        )
    }
}
