package com.example.codexmobile.domain

enum class SessionState {
    IDLE,
    PREPARING_RUNTIME,
    AUTH_REQUIRED,
    READY,
    RUNNING,
    PATCH_PENDING,
    FAILED
}
