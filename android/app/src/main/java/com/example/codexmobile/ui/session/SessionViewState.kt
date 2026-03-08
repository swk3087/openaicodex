package com.example.codexmobile.ui.session

data class SessionViewState(
    val isLoading: Boolean,
    val session: SessionSummary?,
    val errorMessage: String?,
    val terminalLogs: List<String>
) {
    companion object {
        fun initial() = SessionViewState(
            isLoading = true,
            session = null,
            errorMessage = null,
            terminalLogs = emptyList()
        )
    }
}
