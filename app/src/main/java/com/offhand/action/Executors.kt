package com.offhand.action

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.offhand.data.ActionEntity
import com.offhand.data.NoteDao
import com.offhand.data.NoteEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

sealed interface ExecOutcome {
    data object Success : ExecOutcome
    data class Failure(val message: String) : ExecOutcome
}

/** One interface, five implementations (email and bridge arrive in M4/M5). */
interface ActionExecutor {
    suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome
}

// ---------------------------------------------------------------------------

/** Inserts into the device calendar via ContentResolver. Fully offline. */
class CalendarExecutor(private val context: Context) : ActionExecutor {

    override suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED
        ) return ExecOutcome.Failure("Calendar permission not granted")

        val title = slots.subject ?: return ExecOutcome.Failure("Event has no title")
        val iso = slots.datetime ?: return ExecOutcome.Failure("Event has no time")
        val start = try {
            LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            return ExecOutcome.Failure("Unparseable event time")
        }

        val calendarId = findWritableCalendar() ?: createLocalCalendar()
        ?: return ExecOutcome.Failure("No writable calendar on this device")

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + 60 * 60 * 1000)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return if (uri != null) ExecOutcome.Success
        else ExecOutcome.Failure("Calendar insert rejected")
    }

    private fun findWritableCalendar(): Long? {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val access = cursor.getInt(1)
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    return cursor.getLong(0)
                }
            }
        }
        return null
    }

    /** No account on the device (fresh emulator): make a local calendar. */
    private fun createLocalCalendar(): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_NAME, "offhand.local",
            )
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "offhand.local")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "OFFHAND")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "OFFHAND")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF7FDCCB.toInt())
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "offhand.local")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val inserted = context.contentResolver.insert(uri, values) ?: return null
        return ContentUris.parseId(inserted)
    }
}

// ---------------------------------------------------------------------------

/** AlarmManager + local notification. Fully offline. */
class ReminderExecutor(private val context: Context) : ActionExecutor {

    override suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome {
        val body = slots.body ?: return ExecOutcome.Failure("Reminder has no text")
        val iso = slots.datetime ?: return ExecOutcome.Failure("Reminder has no time")
        val triggerAt = try {
            LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            return ExecOutcome.Failure("Unparseable reminder time")
        }
        if (triggerAt < System.currentTimeMillis() - 60_000) {
            return ExecOutcome.Failure("Reminder time is in the past")
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_BODY, body)
            putExtra(ReminderReceiver.EXTRA_ID, entity.id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            entity.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // Exact-alarm permission withheld: a 10-minute window beats crashing.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000, pending)
        }
        return ExecOutcome.Success
    }
}

// ---------------------------------------------------------------------------

/** Saves the note locally in Room. Fully offline. */
class NoteExecutor(private val noteDao: NoteDao) : ActionExecutor {
    override suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome {
        val body = slots.body?.takeIf { it.isNotBlank() }
            ?: return ExecOutcome.Failure("Note is empty")
        noteDao.insert(NoteEntity(body = body, createdAt = System.currentTimeMillis()))
        return ExecOutcome.Success
    }
}
