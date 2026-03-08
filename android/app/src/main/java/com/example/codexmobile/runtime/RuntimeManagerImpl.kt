package com.example.codexmobile.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RuntimeManager {
    override suspend fun prepareRuntime(): RuntimeCheckResult {
        return try {
            ensureDirectory(runtimeRootDir())
            ensureDirectory(sessionRootDir())
            ensureRuntimeMarker()

            val verification = verifyRuntime()
            if (verification.status == RuntimeStatus.READY) {
                verification
            } else {
                RuntimeCheckResult(
                    status = RuntimeStatus.BROKEN,
                    runtimeVersion = RUNTIME_VERSION,
                    errorCode = ERROR_PREPARE_FAILED,
                    errorMessage = verification.errorMessage
                )
            }
        } catch (exception: Throwable) {
            RuntimeCheckResult(
                status = RuntimeStatus.BROKEN,
                runtimeVersion = RUNTIME_VERSION,
                errorCode = ERROR_PREPARE_FAILED,
                errorMessage = exception.message
            )
        }
    }

    override suspend fun verifyRuntime(): RuntimeCheckResult {
        val runtimeDir = runtimeRootDir()
        val sessionsDir = sessionRootDir()
        val marker = runtimeMarkerFile()

        if (!runtimeDir.isDirectory) {
            return broken(ERROR_RUNTIME_DIR_INVALID, "runtime root is missing: ${runtimeDir.path}")
        }

        if (!sessionsDir.isDirectory) {
            return broken(ERROR_SESSIONS_DIR_INVALID, "sessions root is missing: ${sessionsDir.path}")
        }

        if (!marker.isFile) {
            return broken(ERROR_RUNTIME_MARKER_MISSING, "runtime marker is missing: ${marker.path}")
        }

        if (!hasAccess(runtimeDir) || !hasAccess(sessionsDir) || !marker.canRead() || !marker.canWrite()) {
            return broken(ERROR_PERMISSION_DENIED, "runtime or sessions path is not readable/writable")
        }

        val invalidWorkspace = sessionsDir
            .listFiles()
            .orEmpty()
            .firstOrNull { it.isDirectory && !File(it, WORKSPACE_DIR_NAME).isDirectory }

        if (invalidWorkspace != null) {
            return broken(
                ERROR_WORKSPACE_PATH_INVALID,
                "session workspace missing: ${invalidWorkspace.path}/$WORKSPACE_DIR_NAME"
            )
        }

        return RuntimeCheckResult(
            status = RuntimeStatus.READY,
            runtimeVersion = RUNTIME_VERSION
        )
    }

    override suspend fun repairRuntime(): RuntimeCheckResult {
        return try {
            ensureDirectory(runtimeRootDir())
            ensureDirectory(sessionRootDir())
            ensureRuntimeMarker(forceRewrite = true)

            verifyRuntime().let { verification ->
                if (verification.status == RuntimeStatus.READY) {
                    verification
                } else {
                    broken(ERROR_REPAIR_FAILED, verification.errorMessage)
                }
            }
        } catch (exception: Throwable) {
            broken(ERROR_REPAIR_FAILED, exception.message)
        }
    }

    private fun ensureRuntimeMarker(forceRewrite: Boolean = false) {
        val markerFile = runtimeMarkerFile()
        if (forceRewrite || !markerFile.exists()) {
            markerFile.parentFile?.mkdirs()
            markerFile.writeText(RUNTIME_MARKER_CONTENT)
        }
    }

    private fun ensureDirectory(target: File) {
        if (!target.exists() && !target.mkdirs()) {
            throw IOException("Failed to create directory: ${target.path}")
        }

        if (!target.isDirectory) {
            throw IOException("Path is not a directory: ${target.path}")
        }
    }

    private fun hasAccess(target: File): Boolean = target.canRead() && target.canWrite() && target.canExecute()

    private fun runtimeRootDir(): File = File(context.filesDir, RUNTIME_ROOT_PATH)

    private fun sessionRootDir(): File = File(context.filesDir, SESSIONS_ROOT_PATH)

    private fun runtimeMarkerFile(): File = File(runtimeRootDir(), RUNTIME_MARKER_FILE)

    private fun broken(errorCode: String, errorMessage: String?): RuntimeCheckResult {
        return RuntimeCheckResult(
            status = RuntimeStatus.BROKEN,
            runtimeVersion = RUNTIME_VERSION,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    private companion object {
        const val RUNTIME_ROOT_PATH = "runtime/current"
        const val SESSIONS_ROOT_PATH = "sessions"
        const val WORKSPACE_DIR_NAME = "workspace"
        const val RUNTIME_MARKER_FILE = "runtime.ok"
        const val RUNTIME_MARKER_CONTENT = "ready"
        const val RUNTIME_VERSION = "current"

        const val ERROR_PREPARE_FAILED = "RUNTIME_PREPARE_FAILED"
        const val ERROR_RUNTIME_DIR_INVALID = "RUNTIME_DIR_INVALID"
        const val ERROR_SESSIONS_DIR_INVALID = "SESSIONS_DIR_INVALID"
        const val ERROR_RUNTIME_MARKER_MISSING = "RUNTIME_MARKER_MISSING"
        const val ERROR_PERMISSION_DENIED = "RUNTIME_PERMISSION_DENIED"
        const val ERROR_WORKSPACE_PATH_INVALID = "WORKSPACE_PATH_INVALID"
        const val ERROR_REPAIR_FAILED = "RUNTIME_REPAIR_FAILED"
    }
}
