package com.example.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.codexmobile.ui.session.SessionViewModel
import com.example.codexmobile.ui.session.SessionViewState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by sessionViewModel.state.collectAsState()
            MaterialTheme {
                HomeScreen(state)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: SessionViewState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(text = "Codex Mobile", style = MaterialTheme.typography.headlineMedium)

        when {
            state.isLoading -> Text(text = "Loading session...")
            state.errorMessage != null -> Text(text = "Error: ${state.errorMessage}")
            state.session != null -> {
                Text(text = "Session: ${state.session.sessionId}")
                Text(text = "Model: ${state.session.selectedModel}")
                Text(text = "Runtime: ${state.session.runtimeVersion}")
                Text(text = "State: ${state.session.state}")
                Text(text = "Terminal logs: ${state.terminalLogs.size}")
            }
            else -> Text(text = "No session")
        }
    }
}
