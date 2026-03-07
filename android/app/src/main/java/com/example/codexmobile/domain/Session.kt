package com.example.codexmobile.domain

data class Session(
    val id: String,
    val selectedModel: String,
    val runtimeVersion: String,
    val state: SessionState,
    val workspacePath: String,
    val metadata: String
)
