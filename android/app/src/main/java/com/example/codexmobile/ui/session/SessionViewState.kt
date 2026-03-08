package com.example.codexmobile.ui.session

data class SessionViewState(
    val isLoading: Boolean,
    val session: SessionSummary?,
    val errorMessage: String?,
    val terminalLogs: List<String>,
    val commandInput: String,
    val isRunningCommand: Boolean,
    val lastExitCode: Int?,
    val status: SessionExecutionStatus,
    val snackbarMessage: String?
) {
    companion object {
        fun initial() = SessionViewState(
            isLoading = true,
            session = null,
            errorMessage = null,
            terminalLogs = emptyList(),
            commandInput = "",
            isRunningCommand = false,
            lastExitCode = null,
            status = SessionExecutionStatus.READY,
            snackbarMessage = null
        )
    }
}

enum class SessionExecutionStatus {
    READY,
    RUNNING,
    FAILED
}
