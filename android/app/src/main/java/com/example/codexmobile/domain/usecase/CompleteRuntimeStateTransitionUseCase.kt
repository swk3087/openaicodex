package com.example.codexmobile.domain.usecase

import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import javax.inject.Inject

class CompleteRuntimeStateTransitionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: String, isSuccess: Boolean) {
        val state = if (isSuccess) SessionState.READY else SessionState.FAILED
        sessionRepository.updateState(sessionId, state)
    }
}
