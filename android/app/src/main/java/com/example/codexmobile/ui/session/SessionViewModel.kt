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
import kotlinx.coroutines.flow.collect
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
    private val commandInput = MutableStateFlow("")
    private val isRunningCommand = MutableStateFlow(false)
    private val lastExitCode = MutableStateFlow<Int?>(null)
    private val status = MutableStateFlow(SessionExecutionStatus.READY)
    private val snackbarMessage = MutableStateFlow<String?>(null)

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
            errorMessage,
            commandInput,
            isRunningCommand,
            lastExitCode,
            status,
            snackbarMessage
        ) { values ->
            val session = values[0] as SessionSummary?
            val terminalLogs = values[1] as List<String>
            val error = values[2] as String?
            val input = values[3] as String
            val running = values[4] as Boolean
            val exitCode = values[5] as Int?
            val executionStatus = values[6] as SessionExecutionStatus
            val snackbar = values[7] as String?

            SessionViewState(
                isLoading = session == null && error == null,
                session = session,
                errorMessage = error,
                terminalLogs = terminalLogs,
                commandInput = input,
                isRunningCommand = running,
                lastExitCode = exitCode,
                status = executionStatus,
                snackbarMessage = snackbar
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
            terminalSessionManager.open(SESSION_ID, DEFAULT_WORKSPACE_PATH, profile)
        }
    }

    fun updateCommandInput(command: String) {
        commandInput.value = command
    }

    fun runCommand(command: String = commandInput.value) {
        if (isRunningCommand.value) return
        if (command.isBlank()) {
            snackbarMessage.value = "Please enter a command"
            return
        }

        viewModelScope.launch {
            isRunningCommand.value = true
            status.value = SessionExecutionStatus.RUNNING
            lastExitCode.value = null
            snackbarMessage.value = null

            terminalSessionManager.execute(SESSION_ID, command).collect { event ->
                if (event is TerminalOutputEvent.Exit) {
                    lastExitCode.value = event.code
                    isRunningCommand.value = false
                    if (event.code == 0) {
                        status.value = SessionExecutionStatus.READY
                    } else {
                        status.value = SessionExecutionStatus.FAILED
                        snackbarMessage.value = "Command failed (exit code: ${event.code})"
                    }
                }
            }
        }
    }

    fun consumeSnackbarMessage() {
        snackbarMessage.value = null
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
