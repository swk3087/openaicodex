package com.example.codexmobile.runtime

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandGate @Inject constructor(
    private val allowedPrefixes: Set<String> = setOf("node", "npm", "npx codex")
) {
    fun isAllowed(command: String): Boolean {
        val normalized = command.trim()
        return allowedPrefixes.any { normalized.startsWith(it) }
    }
}
