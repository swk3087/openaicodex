package com.example.codexmobile.runtime

import java.io.File

object PathAccessGuard {
    fun assertNotSystemPath(path: String) {
        val normalized = path.trim().ifEmpty { "/" }
        val blocked = BLOCKED_SYSTEM_PREFIXES.any { normalized == it || normalized.startsWith("$it/") }
        require(!blocked) { "SYSTEM_PATH_BLOCKED: $path" }
    }

    fun assertDeleteAllowed(path: String) {
        val normalized = path.trim().removePrefix("/").trimEnd('/')
        require(normalized.isNotBlank()) { "DELETE_ROOT_BLOCKED" }
        require(normalized !in DELETE_BLOCKLIST) { "DELETE_PROTECTED_PATH_BLOCKED: $path" }
    }

    fun assertInsideWorkspace(rootDir: File, target: File, sourcePath: String) {
        val canonicalRoot = rootDir.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(
            canonicalTarget.path.startsWith(canonicalRoot.path + File.separator) ||
                canonicalTarget == canonicalRoot
        ) {
            "PATH_OUT_OF_SCOPE: $sourcePath"
        }
    }

    private val BLOCKED_SYSTEM_PREFIXES = setOf(
        "/system",
        "/proc",
        "/dev",
        "/sys",
        "/apex",
        "/vendor"
    )

    private val DELETE_BLOCKLIST = setOf(
        "Android",
        "Android/data",
        "Android/obb"
    )
}
