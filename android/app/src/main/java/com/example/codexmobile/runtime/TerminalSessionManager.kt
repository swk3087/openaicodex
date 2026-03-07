package com.example.codexmobile.runtime

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
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
    private val blockedExecutableCounts = ConcurrentHashMap<String, Int>()
    private val suggestionWriteTicker = AtomicInteger(0)

    suspend fun open(
        sessionId: String,
        profileId: String = CommandGate.DEFAULT_PROFILE
    ): OpenResult = sessionMutex.withLock {
        if (!SESSION_ID_REGEX.matches(sessionId)) {
            return OpenResult.InvalidSessionId
        }

        val workspace = resolveSessionWorkspace(sessionId)
        if (!workspace.exists()) {
            workspace.mkdirs()
        }

        val resolvedProfileId = resolveSessionProfile(profileId, workspace)
            ?: return OpenResult.InvalidProfile

        val existing = sessions[sessionId]
        if (existing != null && !existing.closed) {
            return OpenResult.AlreadyOpen
        }

        sessions[sessionId] = SessionRuntime(
            sessionId = sessionId,
            workspace = workspace,
            commandProfileId = resolvedProfileId,
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

        val gateResult = commandGate.validate(command, runtime.commandProfileId)
        if (gateResult is CommandGateResult.Blocked) {
            runtime.emitSystem("blocked: ${gateResult.reason}")
            appendCommandFailureLog(
                CommandFailureLog(
                    sessionId = sessionId,
                    profileId = runtime.commandProfileId,
                    command = command,
                    reason = gateResult.reason
                )
            )
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

    private fun resolveSessionProfile(profileId: String, workspace: File): String? {
        if (commandGate.profileExists(profileId)) {
            return profileId
        }

        if (!commandGate.isExtendedProfileId(profileId)) {
            return null
        }

        val projectId = profileId.substringAfter(':', "session")
        val additionalExecutables = readExtendedProfileCommands(workspace)
        return commandGate.registerExtendedProfile(projectId, additionalExecutables)
    }

    private fun readExtendedProfileCommands(workspace: File): Set<String> {
        val configFile = File(workspace, EXTENDED_PROFILE_COMMANDS_FILE)
        if (!configFile.exists()) {
            return emptySet()
        }

        return configFile.readLines()
            .map { it.trim() }
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
            .toSet()
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
            if (code != 0) {
                appendCommandFailureLog(
                    CommandFailureLog(
                        sessionId = runtime.sessionId,
                        profileId = runtime.commandProfileId,
                        command = command,
                        reason = "EXIT_CODE_NON_ZERO",
                        exitCode = code
                    )
                )
            }
        } catch (throwable: Throwable) {
            runtime.emitSystem("execution failed: ${throwable.message}")
            appendCommandFailureLog(
                CommandFailureLog(
                    sessionId = runtime.sessionId,
                    profileId = runtime.commandProfileId,
                    command = command,
                    reason = "EXECUTION_EXCEPTION",
                    exceptionMessage = throwable.message
                )
            )
        } finally {
            runtime.runningProcess = null
        }
    }

    private fun appendCommandFailureLog(entry: CommandFailureLog) {
        val escapedCommand = entry.command.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedReason = entry.reason.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedException = entry.exceptionMessage
            ?.replace("\\", "\\\\")
            ?.replace("\"", "\\\"")
            ?: ""
        val serialized = buildString {
            append("{\"timestampMs\":${entry.timestampMs}")
            append(",\"sessionId\":\"${entry.sessionId}\"")
            append(",\"profileId\":\"${entry.profileId}\"")
            append(",\"command\":\"$escapedCommand\"")
            append(",\"reason\":\"$escapedReason\"")
            append(",\"exitCode\":${entry.exitCode ?: "null"}")
            append(",\"exceptionMessage\":\"$escapedException\"}")
        }

        runCatching {
            val logFile = File(context.filesDir, COMMAND_FAILURE_LOG_FILE)
            logFile.parentFile?.mkdirs()
            logFile.appendText("$serialized\n")
            if (entry.reason == "COMMAND_NOT_ALLOWED") {
                val executable = entry.command.trim().substringBefore(' ').trim()
                if (executable.isNotEmpty()) {
                    persistAllowlistSuggestion(executable)
                }
            }
        }
    }

    private fun persistAllowlistSuggestion(executable: String) {
        blockedExecutableCounts.merge(executable, 1, Int::plus)
        val shouldFlush = suggestionWriteTicker.incrementAndGet() % SUGGESTION_FLUSH_INTERVAL == 0
        if (!shouldFlush) {
            return
        }

        val payload = blockedExecutableCounts.entries
            .sortedByDescending { it.value }
            .joinToString(separator = "\n") { "${it.key},${it.value}" }

        runCatching {
            val suggestionFile = File(context.filesDir, ALLOWLIST_SUGGESTION_FILE)
            suggestionFile.parentFile?.mkdirs()
            suggestionFile.writeText(payload)
        }
    }

    private data class CommandFailureLog(
        val sessionId: String,
        val profileId: String,
        val command: String,
        val reason: String,
        val exitCode: Int? = null,
        val exceptionMessage: String? = null,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private data class SessionRuntime(
        val sessionId: String,
        val workspace: File,
        val commandProfileId: String,
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
        data object InvalidProfile : OpenResult
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
        const val COMMAND_FAILURE_LOG_FILE = "command-failure-log.jsonl"
        const val ALLOWLIST_SUGGESTION_FILE = "allowlist-suggestions.csv"
        const val EXTENDED_PROFILE_COMMANDS_FILE = "extended-allowlist.txt"
        const val SUGGESTION_FLUSH_INTERVAL = 10
    }
}
