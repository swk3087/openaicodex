package com.example.codexmobile.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexmobile.domain.Session
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.domain.SessionState
import com.example.codexmobile.runtime.CommandGate
import com.example.codexmobile.runtime.TerminalSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val terminalSessionManager: TerminalSessionManager
) : ViewModel() {

    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<SessionViewState> =
        combine(
            sessionRepository.observeSession(SESSION_ID)
                .onStart { emit(null) }
                .map { session ->
                    session?.let {
                        SessionSummary(
                            sessionId = it.id,
                            selectedModel = it.selectedModel,
                            runtimeVersion = it.runtimeVersion,
                            state = it.state.name
                        )
                    }
                }
                .catch { throwable ->
                    errorMessage.value = throwable.message ?: "Failed to load session"
                    emit(null)
                },
            terminalSessionManager.stream(SESSION_ID)
                .runningFold(emptyList()) { logs, event ->
                    (logs + "[${event.type}] ${event.content}").takeLast(MAX_TERMINAL_LOGS)
                }
                .onStart { emit(emptyList()) },
            errorMessage
        ) { session, terminalLogs, error ->
            SessionViewState(
                isLoading = session == null && error == null,
                session = session,
                errorMessage = error,
                terminalLogs = terminalLogs
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SessionViewState.initial()
        )

    init {
        viewModelScope.launch {
            ensureDefaultSessionExists()
            terminalSessionManager.open(SESSION_ID, CommandGate.DEFAULT_PROFILE)
        }
    }

    private suspend fun ensureDefaultSessionExists() {
        if (sessionRepository.getSession(SESSION_ID) != null) return

        sessionRepository.upsert(
            Session(
                id = SESSION_ID,
                selectedModel = DEFAULT_MODEL,
                runtimeVersion = DEFAULT_RUNTIME_VERSION,
                state = SessionState.IDLE,
                workspacePath = DEFAULT_WORKSPACE_PATH,
                metadata = DEFAULT_METADATA
            )
        )
    }

    companion object {
        private const val SESSION_ID = "demo-session"
        private const val DEFAULT_MODEL = "gpt-5.2-codex"
        private const val DEFAULT_RUNTIME_VERSION = "-"
        private const val DEFAULT_WORKSPACE_PATH = "/workspace"
        private const val DEFAULT_METADATA = "runtimeErrorCode="
        private const val MAX_TERMINAL_LOGS = 100
    }
}
