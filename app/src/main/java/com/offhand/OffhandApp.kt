package com.offhand

import android.app.Application
import android.app.NotificationManager
import com.offhand.action.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OffhandApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.connectivityObserver.start()
        ReminderReceiver.ensureChannel(
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager,
        )
        appScope.launch {
            // A SENDING item with no recorded result returns to QUEUED.
            container.actionRepository.recoverInterruptedSends()
            // Anything already queued gets a work request on every launch.
            container.dispatcher.drainQueued()
            // Model loads once at app start; PTT presses reuse it.
            container.asrEngine.initialize()
        }
        appScope.launch {
            // Immediate drain the moment connectivity returns.
            container.connectivityObserver.online.collect { online ->
                if (online) container.dispatcher.drainQueued()
            }
        }
    }
}
