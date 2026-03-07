package com.example.codexmobile.runtime

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandGate @Inject constructor(
    private val allowedCommands: Set<String> = setOf("node", "npm", "npx codex")
) {
    fun isAllowed(command: String): Boolean = validate(command) is CommandGateResult.Allowed

    fun validate(command: String): CommandGateResult {
        val normalized = command.trim()
        if (normalized.isEmpty()) {
            return CommandGateResult.Blocked("EMPTY_COMMAND")
        }
        if (META_CHAR_REGEX.containsMatchIn(normalized)) {
            return CommandGateResult.Blocked("UNSAFE_META_CHARACTER")
        }

        return if (allowedCommands.any { normalized.startsWith(it) }) {
            CommandGateResult.Allowed
        } else {
            CommandGateResult.Blocked("COMMAND_NOT_ALLOWED")
        }
    }

    private companion object {
        val META_CHAR_REGEX = Regex("(;|&&|\\|\\||`|\\$\\(|>|<)")
    }
}

sealed interface CommandGateResult {
    data object Allowed : CommandGateResult
    data class Blocked(val reason: String) : CommandGateResult
}
