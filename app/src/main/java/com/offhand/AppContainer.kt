package com.offhand

import android.content.Context
import androidx.room.Room
import com.offhand.action.ActionCoordinator
import com.offhand.action.ActionExecutor
import com.offhand.action.ActionRepository
import com.offhand.action.CalendarExecutor
import com.offhand.action.NoteExecutor
import com.offhand.action.ReminderExecutor
import com.offhand.audio.AsrEngine
import com.offhand.data.ActionDao
import com.offhand.data.ContactDao
import com.offhand.data.NoteDao
import com.offhand.data.OffhandDatabase
import com.offhand.debug.DebugCommand
import com.offhand.dispatch.ConnectivityObserver
import com.offhand.parse.ActionType
import com.offhand.parse.DateResolver
import com.offhand.parse.DeterministicActionParser
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDateTime

/**
 * Plain constructor-injection container. No DI framework by design.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

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

    private val executors: Map<ActionType, ActionExecutor> = mapOf(
        ActionType.CREATE_EVENT to CalendarExecutor(appContext),
        ActionType.SET_REMINDER to ReminderExecutor(appContext),
        ActionType.CAPTURE_NOTE to NoteExecutor(noteDao),
    )

    /** M4 replaces the no-op with the WorkManager dispatcher hook. */
    val coordinator = ActionCoordinator(actionRepository, executors)

    /** Debug-build injection channel; no-op in release. */
    val debugBus = MutableSharedFlow<DebugCommand>(extraBufferCapacity = 8)
}
