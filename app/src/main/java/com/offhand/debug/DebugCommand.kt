package com.offhand.debug

/**
 * Commands injectable in debug builds only (see src/debug DebugHooksReceiver).
 * They feed the exact production pipeline downstream of the microphone, which
 * is how milestone acceptance is scripted on an emulator.
 */
sealed interface DebugCommand {
    data class InjectTranscript(val text: String) : DebugCommand
    data class InjectWav(val path: String) : DebugCommand
}
