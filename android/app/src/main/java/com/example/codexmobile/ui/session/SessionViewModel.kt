package com.example.codexmobile.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexmobile.domain.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionRepository: SessionRepository
) : ViewModel() {
    val summary: StateFlow<SessionSummary> =
        sessionRepository.observeSession(SESSION_ID)
            .map { session ->
                SessionSummary(
                    sessionId = session.id,
                    selectedModel = session.selectedModel,
                    runtimeVersion = session.runtimeVersion,
                    state = session.state.name
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SessionSummary(
                    sessionId = SESSION_ID,
                    selectedModel = "-",
                    runtimeVersion = "-",
                    state = "IDLE"
                )
            )

    companion object {
        private const val SESSION_ID = "demo-session"
    }
}
