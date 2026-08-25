package com.offhand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offhand.action.MAX_ATTEMPTS
import com.offhand.action.StoredSlots
import com.offhand.data.ActionEntity
import com.offhand.data.ActionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm")
private val Amber = Color(0xFFE0B354)

@Composable
fun OutboxScreen(actions: List<ActionEntity>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nothing here yet.\nActions you confirm will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(actions, key = { it.id }) { action ->
                ActionRow(action)
            }
        }
    }
}

@Composable
private fun ActionRow(action: ActionEntity) {
    val slots = runCatching { StoredSlots.decode(action.slotsJson) }.getOrNull()
    val needsAttention =
        action.state == ActionState.FAILED.name && action.attempts >= MAX_ATTEMPTS

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = typeLabel(action, slots),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stateCopy(action),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        needsAttention -> Amber
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            preview(slots)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (needsAttention && action.lastError != null) {
                Text(
                    text = action.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Amber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = timestampFormat.format(
                    Instant.ofEpochMilli(action.createdAt).atZone(ZoneId.systemDefault())
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun typeLabel(action: ActionEntity, slots: StoredSlots?): String =
    when (action.type) {
        "send_email" -> slots?.recipientName?.let { "Email to $it" } ?: "Email"
        "create_event" -> "Calendar event"
        "set_reminder" -> "Reminder"
        "fetch_laptop_file" -> "File from laptop"
        "get_laptop_clipboard" -> "Laptop clipboard"
        "capture_note" -> "Note"
        else -> action.type
    }

/** Per-state copy (master spec M5c). Offline is calm, never an error. */
private fun stateCopy(action: ActionEntity): String = when (action.state) {
    ActionState.DRAFT.name -> "Draft"
    ActionState.CONFIRMED.name -> "Confirmed"
    ActionState.QUEUED.name -> "Saved locally — will send when connected"
    ActionState.SENDING.name -> "Sending…"
    ActionState.DONE.name -> if (action.type == "send_email") "Sent" else "Done"
    ActionState.CANCELLED.name -> "Cancelled"
    ActionState.FAILED.name ->
        if (action.attempts >= MAX_ATTEMPTS) "Needs attention" else "Will retry"
    else -> action.state
}

private fun preview(slots: StoredSlots?): String? =
    slots?.subject ?: slots?.body ?: slots?.pathHint
