package com.example.codexmobile.runtime

class CommandGate(
    private val allowedPrefixes: Set<String> = setOf("node", "npm", "npx codex")
) {
    fun isAllowed(command: String): Boolean {
        val normalized = command.trim()
        return allowedPrefixes.any { normalized.startsWith(it) }
    }
}
