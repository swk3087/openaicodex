package com.example.codexmobile.ui.session

import com.example.codexmobile.runtime.StoragePermissionManager
import com.example.codexmobile.runtime.TerminalSessionManager

enum class SessionDetailTab(val title: String) {
    TERMINAL("Terminal"),
    FILES("Files"),
    DIFFS("Diffs"),
    SETTINGS("Settings"),
    MODEL("Model")
}

data class SessionViewState(
    val summary: SessionSummary,
    val terminalOutput: List<TerminalSessionManager.TerminalOutputEvent>,
    val selectedTab: SessionDetailTab,
    val storageStatus: StoragePermissionManager.StorageModeStatus,
    val runtimePermissionExpansionEnabled: Boolean
) {
    companion object {
        fun initial() = SessionViewState(
            summary = SessionSummary(
                sessionId = "demo-session",
                selectedModel = "-",
                runtimeVersion = "-",
                state = "IDLE",
                runtimeErrorCode = ""
            ),
            terminalOutput = emptyList(),
            selectedTab = SessionDetailTab.TERMINAL,
            storageStatus = StoragePermissionManager.StorageModeStatus(
                configuredMode = StoragePermissionManager.StorageMode.APP_PRIVATE,
                effectiveMode = StoragePermissionManager.StorageMode.APP_PRIVATE,
                hasAllFilesAccess = false,
                isFallbackActive = false
            ),
            runtimePermissionExpansionEnabled = false
        )
    }
}
