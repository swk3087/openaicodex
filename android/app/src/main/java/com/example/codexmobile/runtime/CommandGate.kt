package com.example.codexmobile.runtime

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandGate @Inject constructor() {
    private val commandProfiles = ConcurrentHashMap(DEFAULT_COMMAND_PROFILES)

    fun isAllowed(command: String, profileId: String = DEFAULT_PROFILE): Boolean =
        validate(command, profileId) is CommandGateResult.Allowed

    fun validate(command: String, profileId: String = DEFAULT_PROFILE): CommandGateResult {
        val normalized = command.trim()
        if (normalized.isEmpty()) {
            return CommandGateResult.Blocked("EMPTY_COMMAND")
        }
        if (META_CHAR_REGEX.containsMatchIn(normalized)) {
            return CommandGateResult.Blocked("UNSAFE_META_CHARACTER")
        }

        if (DENYLIST_REGEX.any { it.containsMatchIn(normalized) }) {
            return CommandGateResult.Blocked("DENYLIST_BLOCKED")
        }

        val profile = commandProfiles[profileId] ?: return CommandGateResult.Blocked("UNKNOWN_PROFILE")
        val executable = normalized.substringBefore(' ').trim()
        if (executable.isEmpty()) {
            return CommandGateResult.Blocked("EMPTY_COMMAND")
        }

        return if (profile.allowedExecutables.contains(executable)) {
            CommandGateResult.Allowed(profileId)
        } else {
            CommandGateResult.Blocked("COMMAND_NOT_ALLOWED")
        }
    }

    fun profileExists(profileId: String): Boolean = commandProfiles.containsKey(profileId)

    fun listProfiles(): Set<String> = commandProfiles.keys

    fun registerProfile(profile: CommandProfile) {
        commandProfiles[profile.id] = profile
    }

    fun isExtendedProfileId(profileId: String): Boolean =
        profileId == EXTENDED_PROFILE_PREFIX || profileId.startsWith("$EXTENDED_PROFILE_PREFIX:")

    fun registerExtendedProfile(projectId: String, additionalExecutables: Set<String>): String {
        val normalizedProjectId = projectId.trim()
        require(normalizedProjectId.isNotEmpty()) { "projectId cannot be empty" }
        val profileId = "$EXTENDED_PROFILE_PREFIX:$normalizedProjectId"
        registerProfile(
            CommandProfile(
                id = profileId,
                allowedExecutables = SAFE_BASE_COMMANDS + additionalExecutables
            )
        )
        return profileId
    }

    data class CommandProfile(
        val id: String,
        val allowedExecutables: Set<String>
    )

    companion object {
        const val DEFAULT_PROFILE = "ai-dev-safe"

        private val SAFE_BASE_COMMANDS = setOf(
            "node",
            "npm",
            "npx",
            "pnpm",
            "python",
            "pip",
            "git",
            "rg",
            "cat",
            "ls"
        )

        private val DEFAULT_COMMAND_PROFILES = mapOf(
            DEFAULT_PROFILE to CommandProfile(
                id = DEFAULT_PROFILE,
                allowedExecutables = SAFE_BASE_COMMANDS
            ),
            "ai-dev-extended" to CommandProfile(
                id = "ai-dev-extended",
                allowedExecutables = SAFE_BASE_COMMANDS + setOf(
                    "gradle",
                    "./gradlew",
                    "java",
                    "kotlinc"
                )
            )
        )

        const val EXTENDED_PROFILE_PREFIX = "ai-dev-extended"

        private val DENYLIST_REGEX = listOf(
            Regex("(^|\\s)rm\\s+-rf\\s+/(\\s|$)"),
            Regex("(^|\\s)rm\\s+-rf\\s+/((bin|boot|dev|etc|lib|proc|root|sbin|sys|usr|var))(\\s|$)"),
            Regex("(^|\\s)(mkfs|fdisk|dd)\\b")
        )

        val META_CHAR_REGEX = Regex("(;|&&|\\|\\||`|\\$\\(|>|<)")
    }
}

sealed interface CommandGateResult {
    data class Allowed(val profileId: String) : CommandGateResult
    data class Blocked(val reason: String) : CommandGateResult
}
