package com.example.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.codexmobile.runtime.StoragePermissionManager
import com.example.codexmobile.ui.session.SessionDetailTab
import com.example.codexmobile.ui.session.SessionViewModel
import com.example.codexmobile.ui.session.SessionViewState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: SessionViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value

    SessionScreen(
        state = state,
        onSelectTab = viewModel::selectTab,
        onSetStorageMode = viewModel::updateStorageMode,
        onRefreshStorageState = viewModel::refreshStorageState
    )
}

@Composable
private fun SessionScreen(
    state: SessionViewState,
    onSelectTab: (SessionDetailTab) -> Unit,
    onSetStorageMode: (StoragePermissionManager.StorageMode) -> Unit,
    onRefreshStorageState: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = "Codex Mobile", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Session: ${state.summary.sessionId}")
        Text(text = "Model: ${state.summary.selectedModel}")
        Text(text = "Runtime: ${state.summary.runtimeVersion}")
        Text(text = "State: ${state.summary.state}")

        SessionDetailTabs(selectedTab = state.selectedTab, onSelectTab = onSelectTab)

        when (state.selectedTab) {
            SessionDetailTab.TERMINAL -> {
                Text(text = "--- Terminal ---")
                if (state.runtimePermissionExpansionEnabled) {
                    state.terminalOutput.takeLast(5).forEach { event ->
                        Text(text = "[${event.source}] ${event.text}")
                    }
                } else {
                    Text(text = "Terminal is disabled by feature flag.")
                }
            }

            SessionDetailTab.FILES -> {
                Text(text = "--- Files ---")
                Text(text = "Files tab shell ready.")
            }

            SessionDetailTab.DIFFS -> {
                Text(text = "--- Diffs ---")
                Text(text = "Diffs tab shell ready.")
            }

            SessionDetailTab.SETTINGS -> {
                Text(text = "--- Settings ---")
                Text(text = "Configured Storage Mode: ${state.storageStatus.configuredMode}")
                Text(text = "Effective Workspace Mode: ${state.storageStatus.effectiveMode}")
                if (state.storageStatus.isFallbackActive) {
                    Text(text = "Fallback active: running in app-private workspace because all-files permission is missing.")
                }

                if (state.runtimePermissionExpansionEnabled) {
                    Button(onClick = {
                        onSetStorageMode(StoragePermissionManager.StorageMode.APP_PRIVATE)
                    }) {
                        Text(text = "Use app-private mode (default)")
                    }
                    Button(onClick = {
                        onSetStorageMode(StoragePermissionManager.StorageMode.ALL_FILES)
                    }) {
                        Text(text = "Enable All files mode")
                    }
                    if (
                        StoragePermissionManager.supportsAllFilesPermissionFlow() &&
                        state.storageStatus.configuredMode == StoragePermissionManager.StorageMode.ALL_FILES &&
                        !state.storageStatus.hasAllFilesAccess
                    ) {
                        Button(onClick = {
                            context.startActivity(StoragePermissionManager.createManageAllFilesAccessIntent(context))
                            onRefreshStorageState()
                        }) {
                            Text(text = "Open Android 11+ all-files access settings")
                        }
                    }
                    Button(onClick = onRefreshStorageState) {
                        Text(text = "Refresh storage state")
                    }
                } else {
                    Text(text = "Runtime/permission expansion is disabled by feature flag.")
                }
            }

            SessionDetailTab.MODEL -> {
                Text(text = "--- Model ---")
                Text(text = "Current model: ${state.summary.selectedModel}")
            }
        }
    }
}

@Composable
private fun SessionDetailTabs(
    selectedTab: SessionDetailTab,
    onSelectTab: (SessionDetailTab) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SessionDetailTab.entries) { tab ->
            Button(onClick = { onSelectTab(tab) }) {
                val selectedMarker = if (tab == selectedTab) "●" else "○"
                Text(text = "$selectedMarker ${tab.title}")
            }
        }
    }
}
