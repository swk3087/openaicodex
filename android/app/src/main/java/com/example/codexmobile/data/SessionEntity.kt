package com.example.codexmobile.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val selectedModel: String,
    val runtimeVersion: String,
    val state: String,
    val workspacePath: String,
    val metadata: String
)
