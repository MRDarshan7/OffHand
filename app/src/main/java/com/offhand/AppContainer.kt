package com.offhand

import android.content.Context
import androidx.room.Room
import com.offhand.action.ActionCoordinator
import com.offhand.action.ActionExecutor
import com.offhand.action.ActionRepository
import com.offhand.action.CalendarExecutor
import com.offhand.action.ClipboardExecutor
import com.offhand.action.EmailExecutor
import com.offhand.action.FetchFileExecutor
import com.offhand.action.NoteExecutor
import com.offhand.action.ReminderExecutor
import com.offhand.action.SmtpConfig
import com.offhand.bridge.HttpBridgeClient
import com.offhand.bridge.LaptopBridge
import com.offhand.audio.AsrEngine
import com.offhand.data.ActionDao
import com.offhand.data.ContactDao
import com.offhand.data.NoteDao
import com.offhand.data.OffhandDatabase
import com.offhand.debug.DebugCommand
import com.offhand.dispatch.ConnectivityObserver
import com.offhand.dispatch.Dispatcher
import com.offhand.parse.ActionType
import com.offhand.parse.DateResolver
import com.offhand.parse.DeterministicActionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDateTime

/**
 * Plain constructor-injection container. No DI framework by design.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val database: OffhandDatabase = Room.databaseBuilder(
        appContext,
        OffhandDatabase::class.java,
        "offhand.db",
    )
        .addCallback(OffhandDatabase.seedCallback)
        .fallbackToDestructiveMigration()
        .build()

    val actionDao: ActionDao get() = database.actionDao()
    val contactDao: ContactDao get() = database.contactDao()
    val noteDao: NoteDao get() = database.noteDao()

    val connectivityObserver = ConnectivityObserver(appContext)

    val dateResolver = DateResolver { LocalDateTime.now() }
    val parser = DeterministicActionParser(dateResolver)

    val asrEngine = AsrEngine(appContext)

    val actionRepository = ActionRepository(actionDao)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val executors: Map<ActionType, ActionExecutor> = mapOf(
        ActionType.CREATE_EVENT to CalendarExecutor(appContext),
        ActionType.SET_REMINDER to ReminderExecutor(appContext),
        ActionType.CAPTURE_NOTE to NoteExecutor(noteDao),
    )

    val laptopBridge: LaptopBridge =
        HttpBridgeClient(BuildConfig.BRIDGE_HOST, BuildConfig.BRIDGE_PORT)

    /** Executors that need a network; the dispatch worker looks up here. */
    val networkExecutors: Map<ActionType, ActionExecutor> = mapOf(
        ActionType.SEND_EMAIL to EmailExecutor(
            SmtpConfig(
                host = BuildConfig.SMTP_HOST,
                port = BuildConfig.SMTP_PORT,
                username = BuildConfig.SMTP_USER,
                password = BuildConfig.SMTP_PASS,
                from = BuildConfig.SMTP_FROM,
                starttls = BuildConfig.SMTP_STARTTLS,
            ),
        ),
        ActionType.FETCH_LAPTOP_FILE to FetchFileExecutor(
            laptopBridge,
            actionDao,
            appContext.filesDir.resolve("bridge"),
        ),
        ActionType.GET_LAPTOP_CLIPBOARD to ClipboardExecutor(laptopBridge, noteDao),
    )

    val dispatcher = Dispatcher(appContext, actionRepository, appScope)

    val coordinator = ActionCoordinator(
        actionRepository,
        executors,
        onQueued = { actionId -> dispatcher.enqueue(actionId) },
    )

    /** Debug-build injection channel; no-op in release. */
    val debugBus = MutableSharedFlow<DebugCommand>(extraBufferCapacity = 8)
}
