package com.example.codexmobile.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SessionScreen(
    state: SessionViewState,
    onCommandChange: (String) -> Unit,
    onExecuteClick: (String) -> Unit,
    onSnackbarConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            onSnackbarConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Session", style = MaterialTheme.typography.headlineSmall)
            StatusBadge(status = state.status)

            if (state.isLoading) {
                CircularProgressIndicator()
            }

            state.errorMessage?.let {
                Text(text = "Error: $it", color = MaterialTheme.colorScheme.error)
            }

            state.session?.let { session ->
                Text(text = "Session: ${session.sessionId}")
                Text(text = "Model: ${session.selectedModel}")
                Text(text = "Runtime: ${session.runtimeVersion}")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = state.commandInput,
                    onValueChange = onCommandChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Command") },
                    enabled = !state.isRunningCommand,
                    singleLine = true
                )
                Button(
                    onClick = { onExecuteClick(state.commandInput) },
                    enabled = !state.isRunningCommand,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Execute")
                }
            }

            state.lastExitCode?.let { code ->
                Text(text = "Exit code: $code")
            }

            Text(text = "Terminal Output", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF111111))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.terminalLogs) { log ->
                    Text(text = log, color = Color(0xFFE0E0E0), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SessionExecutionStatus) {
    val tone = when (status) {
        SessionExecutionStatus.READY -> Color(0xFF2E7D32)
        SessionExecutionStatus.RUNNING -> Color(0xFF1565C0)
        SessionExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(status.name) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = tone,
            labelColor = Color.White,
            disabledContainerColor = tone,
            disabledLabelColor = Color.White
        )
    )
}
