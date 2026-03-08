package com.example.codexmobile.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.runtime.CommandGate
import com.example.codexmobile.runtime.TerminalOutputEvent
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
                    (logs + event.toDisplayLog()).takeLast(MAX_TERMINAL_LOGS)
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
            val session = sessionRepository.getSession(SESSION_ID)
            val profile = session?.metadata.toProfileOrDefault()
            terminalSessionManager.openSession(SESSION_ID, DEFAULT_WORKSPACE_PATH, profile)
        }
    }

    private suspend fun ensureDefaultSessionExists() {
        sessionRepository.createIfAbsent(SESSION_ID, DEFAULT_MODEL, DEFAULT_WORKSPACE_PATH)
    }


    private fun String?.toProfileOrDefault(): CommandGate.Profile {
        val metadata = this?.trim().orEmpty()
        if (metadata.isEmpty()) {
            return CommandGate.Profile.SAFE
        }

        val directProfile = runCatching { CommandGate.Profile.valueOf(metadata) }.getOrNull()
        if (directProfile != null) {
            return directProfile
        }

        val profileEntry = metadata
            .split(",")
            .map { it.trim() }
            .firstOrNull { entry -> entry.startsWith("profile=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            .orEmpty()

        return runCatching { CommandGate.Profile.valueOf(profileEntry.uppercase()) }.getOrDefault(CommandGate.Profile.SAFE)
    }

    private fun TerminalOutputEvent.toDisplayLog(): String = when (this) {
        is TerminalOutputEvent.Stdout -> "[stdout] $text"
        is TerminalOutputEvent.Stderr -> "[stderr] $text"
        is TerminalOutputEvent.Exit -> "[exit] $code"
    }

    companion object {
        private const val SESSION_ID = "demo-session"
        private const val DEFAULT_MODEL = "gpt-5.2-codex"
        private const val DEFAULT_WORKSPACE_PATH = "/workspace"
        private const val MAX_TERMINAL_LOGS = 100
    }
}
