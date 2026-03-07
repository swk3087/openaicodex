package com.example.codexmobile.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RuntimeManager {
    override suspend fun prepareRuntime(): RuntimeCheckResult {
        ensureRuntimeDirectory()
        val verification = verifyRuntime()
        if (verification.status == RuntimeStatus.READY) {
            return verification
        }

        val repaired = repairRuntime()
        return if (repaired.status == RuntimeStatus.READY) {
            repaired
        } else {
            RuntimeCheckResult(
                status = RuntimeStatus.BROKEN,
                runtimeVersion = expectedVersion,
                errorCode = ERROR_PREPARE_FAILED
            )
        }
    }

    override suspend fun verifyRuntime(): RuntimeCheckResult {
        val versionFile = versionFile()
        if (!versionFile.exists()) {
            return RuntimeCheckResult(
                status = RuntimeStatus.BROKEN,
                runtimeVersion = expectedVersion,
                errorCode = ERROR_RUNTIME_FILE_MISSING
            )
        }

        val installedVersion = versionFile.readText().trim()
        if (installedVersion != expectedVersion) {
            return RuntimeCheckResult(
                status = RuntimeStatus.BROKEN,
                runtimeVersion = installedVersion.ifBlank { expectedVersion },
                errorCode = ERROR_VERSION_MISMATCH
            )
        }

        return RuntimeCheckResult(
            status = RuntimeStatus.READY,
            runtimeVersion = installedVersion
        )
    }

    override suspend fun repairRuntime(): RuntimeCheckResult {
        ensureRuntimeDirectory()
        return try {
            versionFile().writeText(expectedVersion)
            RuntimeCheckResult(
                status = RuntimeStatus.READY,
                runtimeVersion = expectedVersion
            )
        } catch (_: Throwable) {
            RuntimeCheckResult(
                status = RuntimeStatus.BROKEN,
                runtimeVersion = expectedVersion,
                errorCode = ERROR_REPAIR_FAILED
            )
        }
    }

    private fun runtimeDir(): File = File(context.filesDir, RUNTIME_DIR)

    private fun ensureRuntimeDirectory() {
        val dir = runtimeDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }

    private fun versionFile(): File = File(runtimeDir(), VERSION_FILE)

    private companion object {
        const val RUNTIME_DIR = "runtime"
        const val VERSION_FILE = "version.txt"
        const val ERROR_RUNTIME_FILE_MISSING = "RUNTIME_FILE_MISSING"
        const val ERROR_VERSION_MISMATCH = "RUNTIME_VERSION_MISMATCH"
        const val ERROR_REPAIR_FAILED = "RUNTIME_REPAIR_FAILED"
        const val ERROR_PREPARE_FAILED = "RUNTIME_PREPARE_FAILED"
        const val expectedVersion = "1.0.0"
    }
}
