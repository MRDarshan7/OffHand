package com.offhand.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.offhand.OffhandApp

/**
 * Debug-build injection points, used by the scripted emulator checks:
 *
 *   adb shell am broadcast -a com.offhand.DEBUG_TRANSCRIPT --es text "remind me ..." com.offhand
 *   adb shell am broadcast -a com.offhand.DEBUG_WAV --es path /sdcard/... com.offhand
 *
 * They feed the production pipeline downstream of the microphone. Absent from
 * release builds.
 */
class DebugHooksReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? OffhandApp ?: return
        when (intent.action) {
            "com.offhand.DEBUG_TRANSCRIPT" -> intent.getStringExtra("text")?.let {
                app.container.debugBus.tryEmit(DebugCommand.InjectTranscript(it))
            }

            "com.offhand.DEBUG_WAV" -> intent.getStringExtra("path")?.let {
                app.container.debugBus.tryEmit(DebugCommand.InjectWav(it))
            }
        }
    }
}
