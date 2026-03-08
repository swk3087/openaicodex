package com.example.codexmobile.runtime

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Singleton
class TerminalSessionManager @Inject constructor(
    private val commandGate: CommandGate
) {
    private val sessions = ConcurrentHashMap<String, SessionConfig>()
    private val activeProcesses = ConcurrentHashMap<String, Process>()
    private val eventStreams = ConcurrentHashMap<String, MutableSharedFlow<TerminalOutputEvent>>()

    fun openSession(
        sessionId: String,
        workingDir: String,
        profile: CommandGate.Profile = CommandGate.Profile.SAFE
    ) {
        val dir = File(workingDir)
        sessions[sessionId] = SessionConfig(dir, profile)
        eventStreams.putIfAbsent(
            sessionId,
            MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
        )
    }

    fun execute(sessionId: String, command: String): Flow<TerminalOutputEvent> = flow {
        val sessionConfig = sessions[sessionId]
        if (sessionConfig == null) {
            emit(TerminalOutputEvent.Stderr("Session not opened: $sessionId"))
            emit(TerminalOutputEvent.Exit(MISSING_SESSION_EXIT_CODE))
            return@flow
        }

        if (!commandGate.isAllowed(command, sessionConfig.profile)) {
            emit(TerminalOutputEvent.Stderr("Command rejected by gate: $command"))
            emit(TerminalOutputEvent.Exit(REJECTED_COMMAND_EXIT_CODE))
            return@flow
        }

        executeProcess(sessionId, command, sessionConfig.workingDir).collect { event ->
            emit(event)
            eventStreams[sessionId]?.tryEmit(event)
        }
    }

    fun closeSession(sessionId: String) {
        activeProcesses.remove(sessionId)?.destroy()
        sessions.remove(sessionId)
        eventStreams.remove(sessionId)
    }

    fun stream(sessionId: String): Flow<TerminalOutputEvent> =
        eventStreams[sessionId]?.asSharedFlow() ?: emptyFlow()

    private fun executeProcess(
        sessionId: String,
        command: String,
        workingDir: File
    ): Flow<TerminalOutputEvent> = channelFlow {
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(workingDir)
                .start()

            activeProcesses[sessionId]?.destroy()
            activeProcesses[sessionId] = process

            coroutineScope {
                launch {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { trySend(TerminalOutputEvent.Stdout(it)) }
                    }
                }
                launch {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { trySend(TerminalOutputEvent.Stderr(it)) }
                    }
                }
            }

            val exitCode = process.waitFor()
            activeProcesses.remove(sessionId)
            trySend(TerminalOutputEvent.Exit(exitCode))
        }
    }


    private data class SessionConfig(
        val workingDir: File,
        val profile: CommandGate.Profile
    )

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 128
        private const val REJECTED_COMMAND_EXIT_CODE = 126
        private const val MISSING_SESSION_EXIT_CODE = 127
    }
}
