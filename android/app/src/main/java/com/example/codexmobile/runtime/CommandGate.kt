package com.example.codexmobile.runtime

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandGate @Inject constructor(
    private val safeAllowedPrefixes: Set<String> = setOf("node", "npm", "npx codex"),
    private val aiDevAllowedPrefixes: Set<String> = setOf(
        "node",
        "npm",
        "npx",
        "pnpm",
        "git",
        "python",
        "pip",
        "rg",
        "ls",
        "cat"
    )
) {
    enum class Profile {
        SAFE,
        AI_DEV
    }

    fun isAllowed(command: String, profile: Profile = Profile.SAFE): Boolean {
        val normalized = command.trim()
        if (normalized.isEmpty()) {
            return false
        }
        if (denyPatterns.any { pattern -> pattern.containsMatchIn(normalized) }) {
            return false
        }

        val allowedPrefixes = when (profile) {
            Profile.SAFE -> safeAllowedPrefixes
            Profile.AI_DEV -> aiDevAllowedPrefixes
        }

        return allowedPrefixes.any { prefix ->
            normalized == prefix || normalized.startsWith("$prefix ")
        }
    }

    companion object {
        private val denyPatterns = listOf(
            Regex("""(^|\s)rm\s+-rf\s+/($|\s)"""),
            Regex("""(^|\s)rm\s+-rf\s+--no-preserve-root($|\s)"""),
            Regex("""(^|\s)(mkfs(\.[^\s]+)?|fdisk|parted)\b"""),
            Regex("""(^|\s)chmod\s+-R\s+777\s+/($|\s)"""),
            Regex("""(^|\s)chown\s+-R\s+root(:root)?\s+/($|\s)""")
        )
    }
}
