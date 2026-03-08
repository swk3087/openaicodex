package com.example.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.codexmobile.ui.session.SessionSummary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(
                    SessionSummary(
                        sessionId = "demo-session",
                        selectedModel = "gpt-4.1",
                        runtimeVersion = "node-20 / codex-latest",
                        state = "READY"
                    )
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(summary: SessionSummary) {
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
    }
}
