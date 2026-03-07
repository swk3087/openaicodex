package com.example.codexmobile.runtime

class TerminalSessionManager(
    private val commandGate: CommandGate
) {
    fun validateCommand(command: String): Boolean = commandGate.isAllowed(command)
}
