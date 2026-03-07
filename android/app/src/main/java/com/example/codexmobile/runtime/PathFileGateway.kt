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
    @ApplicationContext context: Context
) : FileGateway {
    private val rootDir: File = context.filesDir

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
                        path = child.relativeTo(rootDir).invariantSeparatorsPath,
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

    private fun resolve(path: String): File {
        val relativePath = path.removePrefix("/")
        val target = File(rootDir, relativePath)
        val canonicalTarget = target.canonicalFile
        require(canonicalTarget.path.startsWith(rootDir.canonicalPath + File.separator) || canonicalTarget == rootDir.canonicalFile) {
            "PATH_OUT_OF_SCOPE: $path"
        }
        return canonicalTarget
    }
}
