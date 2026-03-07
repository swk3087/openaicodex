package com.example.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.codexmobile.runtime.StoragePermissionManager
import com.example.codexmobile.runtime.TerminalSessionManager
import com.example.codexmobile.ui.session.SessionSummary
import com.example.codexmobile.ui.session.SessionViewModel
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
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val terminalOutput by viewModel.terminalOutput.collectAsStateWithLifecycle()

    SessionScreen(summary = summary, terminalOutput = terminalOutput)
}

@Composable
private fun SessionScreen(
    summary: SessionSummary,
    terminalOutput: List<TerminalSessionManager.TerminalOutputEvent>
) {
    val context = LocalContext.current
    var storageStatus by remember { mutableStateOf(StoragePermissionManager.currentStatus(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = "Codex Mobile", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Session: ${summary.sessionId}")
        Text(text = "Model: ${summary.selectedModel}")
        Text(text = "Runtime: ${summary.runtimeVersion}")
        Text(text = "State: ${summary.state}")
        Text(text = "Runtime Error: ${summary.runtimeErrorCode.ifBlank { "-" }}")
        Text(text = "Configured Storage Mode: ${storageStatus.configuredMode}")
        Text(text = "Effective Workspace Mode: ${storageStatus.effectiveMode}")
        if (storageStatus.isFallbackActive) {
            Text(text = "Fallback active: running in app-private workspace because all-files permission is missing.")
        }

        Text(text = "--- Storage Settings ---")
        Button(onClick = {
            StoragePermissionManager.saveConfiguredStorageMode(
                context,
                StoragePermissionManager.StorageMode.APP_PRIVATE
            )
            storageStatus = StoragePermissionManager.currentStatus(context)
        }) {
            Text(text = "Use app-private mode (default)")
        }
        Button(onClick = {
            StoragePermissionManager.saveConfiguredStorageMode(
                context,
                StoragePermissionManager.StorageMode.ALL_FILES
            )
            storageStatus = StoragePermissionManager.currentStatus(context)
        }) {
            Text(text = "Enable All files mode")
        }
        if (
            StoragePermissionManager.supportsAllFilesPermissionFlow() &&
            storageStatus.configuredMode == StoragePermissionManager.StorageMode.ALL_FILES &&
            !storageStatus.hasAllFilesAccess
        ) {
            Button(onClick = {
                context.startActivity(StoragePermissionManager.createManageAllFilesAccessIntent(context))
                storageStatus = StoragePermissionManager.currentStatus(context)
            }) {
                Text(text = "Open Android 11+ all-files access settings")
            }
        }
        Button(onClick = {
            storageStatus = StoragePermissionManager.currentStatus(context)
        }) {
            Text(text = "Refresh storage state")
        }

        Text(text = "--- Terminal Stream ---")
        terminalOutput.takeLast(5).forEach { event ->
            Text(text = "[${event.source}] ${event.text}")
        }
    }
}
