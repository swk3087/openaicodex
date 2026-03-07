package com.example.codexmobile.runtime

interface FileGateway {
    suspend fun list(path: String): List<FileEntry>

    suspend fun read(path: String): ByteArray

    suspend fun write(path: String, data: ByteArray)

    suspend fun delete(path: String)

    data class FileEntry(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val sizeBytes: Long
    )
}
