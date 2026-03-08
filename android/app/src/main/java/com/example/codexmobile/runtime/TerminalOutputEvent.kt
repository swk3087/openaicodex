package com.example.codexmobile.runtime

sealed interface TerminalOutputEvent {
    data class Stdout(val text: String) : TerminalOutputEvent
    data class Stderr(val text: String) : TerminalOutputEvent
    data class Exit(val code: Int) : TerminalOutputEvent
}
