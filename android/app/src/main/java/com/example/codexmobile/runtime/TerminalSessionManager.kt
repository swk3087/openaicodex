package com.example.codexmobile.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TerminalSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commandGate: CommandGate
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()
    private val sessions = ConcurrentHashMap<String, SessionRuntime>()
    private val sessionLogs = ConcurrentHashMap<String, MutableSharedFlow<TerminalOutputEvent>>()

    suspend fun open(sessionId: String): OpenResult = sessionMutex.withLock {
        if (!SESSION_ID_REGEX.matches(sessionId)) {
            return OpenResult.InvalidSessionId
        }

        val existing = sessions[sessionId]
        if (existing != null && !existing.closed) {
            return OpenResult.AlreadyOpen
        }

        val workspace = resolveSessionWorkspace(sessionId)
        if (!workspace.exists()) {
            workspace.mkdirs()
        }

        sessions[sessionId] = SessionRuntime(
            sessionId = sessionId,
            workspace = workspace,
            logs = sessionLogs.getOrPut(sessionId) {
                MutableSharedFlow(extraBufferCapacity = OUTPUT_BUFFER_SIZE)
            }
        )
        OpenResult.Opened(workspace.absolutePath)
    }

    suspend fun execute(sessionId: String, command: String): ExecuteResult {
        val runtime = sessions[sessionId] ?: return ExecuteResult(
            accepted = false,
            blockedReason = "SESSION_NOT_OPEN"
        )

        if (runtime.closed) {
            return ExecuteResult(
                accepted = false,
                blockedReason = "SESSION_CLOSED"
            )
        }

        val gateResult = commandGate.validate(command)
        if (gateResult is CommandGateResult.Blocked) {
            runtime.emitSystem("blocked: ${gateResult.reason}")
            return ExecuteResult(accepted = false, blockedReason = gateResult.reason)
        }

        runtime.runningProcess?.destroy()
        runtime.runningProcess = null

        managerScope.launch {
            runCommand(runtime, command)
        }

        return ExecuteResult(
            accepted = true,
            commandId = "${sessionId}-${System.currentTimeMillis()}"
        )
    }

    suspend fun close(sessionId: String) = sessionMutex.withLock {
        val runtime = sessions.remove(sessionId) ?: return
        runtime.closed = true
        runtime.runningProcess?.let { process ->
            process.destroy()
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
        runtime.runningProcess = null
        runtime.emitSystem("session closed")
    }

    fun stream(sessionId: String): Flow<TerminalOutputEvent> =
        sessionLogs.getOrPut(sessionId) {
            MutableSharedFlow(extraBufferCapacity = OUTPUT_BUFFER_SIZE)
        }.asSharedFlow()

    fun shutdown() {
        managerScope.cancel()
        sessions.clear()
    }

    private fun resolveSessionWorkspace(sessionId: String): File {
        val root = StoragePermissionManager.workspaceRoot(context)
        val target = File(root, "sessions/$sessionId")
        PathAccessGuard.assertInsideWorkspace(root, target, "sessions/$sessionId")
        return target.canonicalFile
    }

    private suspend fun runCommand(runtime: SessionRuntime, command: String) {
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(runtime.workspace)
                .start()
            runtime.runningProcess = process
            runtime.emitSystem("executing: $command")

            coroutineScope {
                launch {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> runtime.emit(OutputSource.STDOUT, line) }
                    }
                }
                launch {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> runtime.emit(OutputSource.STDERR, line) }
                    }
                }
            }

            val code = process.waitFor()
            runtime.emitSystem("exit code: $code")
        } catch (throwable: Throwable) {
            runtime.emitSystem("execution failed: ${throwable.message}")
        } finally {
            runtime.runningProcess = null
        }
    }

    private data class SessionRuntime(
        val sessionId: String,
        val workspace: File,
        val logs: MutableSharedFlow<TerminalOutputEvent>,
        var runningProcess: Process? = null,
        var closed: Boolean = false
    ) {
        fun emit(source: OutputSource, text: String) {
            logs.tryEmit(
                TerminalOutputEvent(
                    source = source,
                    text = text,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }

        fun emitSystem(text: String) = emit(OutputSource.SYSTEM, text)
    }

    sealed interface OpenResult {
        data class Opened(val workspacePath: String) : OpenResult
        data object AlreadyOpen : OpenResult
        data object InvalidSessionId : OpenResult
    }

    data class ExecuteResult(
        val accepted: Boolean,
        val blockedReason: String? = null,
        val commandId: String? = null
    )

    data class TerminalOutputEvent(
        val source: OutputSource,
        val text: String,
        val timestampMs: Long
    )

    enum class OutputSource {
        STDOUT,
        STDERR,
        SYSTEM
    }

    private companion object {
        val SESSION_ID_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
        const val OUTPUT_BUFFER_SIZE = 128
    }
}
