package com.example.codexmobile.ui.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.codexmobile.flags.FeatureFlags
import com.example.codexmobile.domain.SessionRepository
import com.example.codexmobile.runtime.CommandGate
import com.example.codexmobile.runtime.StoragePermissionManager
import com.example.codexmobile.runtime.TerminalSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val terminalSessionManager: TerminalSessionManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private var sessionCommandProfile: String = CommandGate.DEFAULT_PROFILE
    private val selectedTab = MutableStateFlow(SessionDetailTab.TERMINAL)
    private val storageStatus = MutableStateFlow(StoragePermissionManager.currentStatus(appContext))

    val state: StateFlow<SessionViewState> =
        combine(
            sessionRepository.observeSession(SESSION_ID)
                .map { session ->
                    SessionSummary(
                        sessionId = session.id,
                        selectedModel = session.selectedModel,
                        runtimeVersion = session.runtimeVersion,
                        state = session.state.name,
                        runtimeErrorCode = session.metadata.removePrefix("runtimeErrorCode=")
                    )
                },
            terminalSessionManager.stream(SESSION_ID)
                .runningFold(emptyList()) { acc, event ->
                    (acc + event).takeLast(MAX_TERMINAL_EVENTS)
                },
            selectedTab,
            storageStatus
        ) { summary, terminalOutput, tab, status ->
            SessionViewState(
                summary = summary,
                terminalOutput = terminalOutput,
                selectedTab = tab,
                storageStatus = status,
                runtimePermissionExpansionEnabled = FeatureFlags.runtimePermissionExpansionEnabled
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SessionViewState.initial()
            )

    init {
        if (FeatureFlags.runtimePermissionExpansionEnabled) {
            viewModelScope.launch {
                terminalSessionManager.open(SESSION_ID, sessionCommandProfile)
            }
        }
    }

    fun selectTab(tab: SessionDetailTab) {
        selectedTab.update { tab }
    }

    fun selectCommandProfile(profileId: String) {
        if (!FeatureFlags.runtimePermissionExpansionEnabled) return
        viewModelScope.launch {
            terminalSessionManager.close(SESSION_ID)
            sessionCommandProfile = profileId
            terminalSessionManager.open(SESSION_ID, sessionCommandProfile)
        }
    }

    fun execute(command: String) {
        if (!FeatureFlags.runtimePermissionExpansionEnabled) return
        viewModelScope.launch {
            terminalSessionManager.execute(SESSION_ID, command)
        }
    }

    fun updateStorageMode(mode: StoragePermissionManager.StorageMode) {
        if (!FeatureFlags.runtimePermissionExpansionEnabled) return
        StoragePermissionManager.saveConfiguredStorageMode(appContext, mode)
        refreshStorageState()
    }

    fun refreshStorageState() {
        storageStatus.update { StoragePermissionManager.currentStatus(appContext) }
    }

    fun closeSession() {
        viewModelScope.launch {
            terminalSessionManager.close(SESSION_ID)
        }
    }

    companion object {
        private const val SESSION_ID = "demo-session"
        private const val MAX_TERMINAL_EVENTS = 100
    }
}
