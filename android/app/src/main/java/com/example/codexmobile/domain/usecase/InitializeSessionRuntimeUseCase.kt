package com.example.codexmobile.domain.usecase

import com.example.codexmobile.domain.SessionRepository
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
        sessionRepository.createIfAbsent(sessionId, DEFAULT_MODEL, DEFAULT_WORKSPACE_PATH)

        prepareRuntimeStateTransition(sessionId)
        val result = runtimeManager.prepareRuntime()

        sessionRepository.updateRuntimeVersion(sessionId, result.runtimeVersion)
        sessionRepository.updateError(sessionId, result.errorCode, result.errorMessage)
        val errorCode = result.errorCode.orEmpty()
        sessionRepository.updateMetadata(sessionId, "runtimeErrorCode=$errorCode")

        completeRuntimeStateTransition(sessionId, result.status == RuntimeStatus.READY)
    }

    private companion object {
        const val DEFAULT_MODEL = "gpt-5.2-codex"
        const val DEFAULT_WORKSPACE_PATH = "/workspace"
    }
}
