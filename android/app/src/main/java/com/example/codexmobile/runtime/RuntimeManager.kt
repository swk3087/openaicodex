package com.example.codexmobile.runtime

interface RuntimeManager {
    suspend fun prepareRuntime(): RuntimeCheckResult
    suspend fun verifyRuntime(): RuntimeCheckResult
    suspend fun repairRuntime(): RuntimeCheckResult
}

data class RuntimeCheckResult(
    val status: RuntimeStatus,
    val runtimeVersion: String,
    val errorCode: String? = null
)

enum class RuntimeStatus {
    READY,
    REPAIRING,
    BROKEN
}
