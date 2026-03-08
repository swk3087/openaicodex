package com.example.codexmobile.domain.usecase

import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import javax.inject.Inject

class PrepareRuntimeStateTransitionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(sessionId: String) {
        sessionRepository.updateState(sessionId, SessionState.PREPARING_RUNTIME)
    }
}
