package com.example.codexmobile.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.runtime.TerminalSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val terminalSessionManager: TerminalSessionManager
) : ViewModel() {
    val summary: StateFlow<SessionSummary> =
        sessionRepository.observeSession(SESSION_ID)
            .map { session ->
                SessionSummary(
                    sessionId = session.id,
                    selectedModel = session.selectedModel,
                    runtimeVersion = session.runtimeVersion,
                    state = session.state.name,
                    runtimeErrorCode = session.metadata.removePrefix("runtimeErrorCode=")
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SessionSummary(
                    sessionId = SESSION_ID,
                    selectedModel = "-",
                    runtimeVersion = "-",
                    state = "IDLE",
                    runtimeErrorCode = ""
                )
            )

    val terminalOutput: StateFlow<List<TerminalSessionManager.TerminalOutputEvent>> =
        terminalSessionManager.stream(SESSION_ID)
            .runningFold(emptyList()) { acc, event ->
                (acc + event).takeLast(MAX_TERMINAL_EVENTS)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )


    init {
        viewModelScope.launch {
            terminalSessionManager.open(SESSION_ID, SESSION_COMMAND_PROFILE)
        }
    }

    fun execute(command: String) {
        viewModelScope.launch {
            terminalSessionManager.execute(SESSION_ID, command)
        }
    }

    fun closeSession() {
        viewModelScope.launch {
            terminalSessionManager.close(SESSION_ID)
        }
    }

    companion object {
        private const val SESSION_ID = "demo-session"
        private const val MAX_TERMINAL_EVENTS = 100
        private const val SESSION_COMMAND_PROFILE = "ai-dev-safe"
    }
}
