package com.example.codexmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.example.codexmobile.ui.session.SessionScreen
import com.example.codexmobile.ui.session.SessionViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val sessionViewModel: SessionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by sessionViewModel.state.collectAsState()
            MaterialTheme {
                SessionScreen(
                    state = state,
                    onCommandChange = sessionViewModel::updateCommandInput,
                    onExecuteClick = sessionViewModel::runCommand,
                    onSnackbarConsumed = sessionViewModel::consumeSnackbarMessage
                )
            }
        }
    }
}
