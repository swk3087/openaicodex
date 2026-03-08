package com.example.codexmobile.runtime

interface RuntimeManager {
    suspend fun prepareRuntime(): RuntimeStatus
    suspend fun verifyRuntime(): RuntimeStatus
    suspend fun repairRuntime(): RuntimeStatus
}

enum class RuntimeStatus {
    READY,
    REPAIRING,
    BROKEN
}
