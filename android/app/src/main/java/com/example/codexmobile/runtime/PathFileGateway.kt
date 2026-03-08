package com.example.codexmobile.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PathFileGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : FileGateway {
    override suspend fun list(path: String): List<FileGateway.FileEntry> = withContext(Dispatchers.IO) {
        val directory = resolve(path)
        if (!directory.exists()) {
            emptyList()
        } else {
            require(directory.isDirectory) { "NOT_A_DIRECTORY: $path" }
            directory
                .listFiles()
                .orEmpty()
                .sortedBy { it.name }
                .map { child ->
                    FileGateway.FileEntry(
                        name = child.name,
                        path = child.relativeTo(rootDir()).invariantSeparatorsPath,
                        isDirectory = child.isDirectory,
                        sizeBytes = child.length()
                    )
                }
        }
    }

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        val target = resolve(path)
        require(target.exists()) { "FILE_NOT_FOUND: $path" }
        require(target.isFile) { "NOT_A_FILE: $path" }
        target.readBytes()
    }

    override suspend fun write(path: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val target = resolve(path)
        target.parentFile?.mkdirs()
        target.writeBytes(data)
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        PathAccessGuard.assertDeleteAllowed(path)
        val target = resolve(path)
        require(target.exists()) { "FILE_NOT_FOUND: $path" }
        target.deleteRecursively()
    }

    private fun resolve(path: String): File {
        val sanitizedPath = path.trim()
        PathAccessGuard.assertNotSystemPath(sanitizedPath)
        val relativePath = sanitizedPath.removePrefix("/")
        val rootDir = rootDir()
        val target = File(rootDir, relativePath)
        PathAccessGuard.assertInsideWorkspace(rootDir, target, path)
        return target.canonicalFile
    }

    private fun rootDir(): File = StoragePermissionManager.workspaceRoot(context)
}
