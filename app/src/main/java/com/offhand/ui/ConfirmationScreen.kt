package com.offhand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.offhand.parse.ActionType
import com.offhand.parse.Contact
import com.offhand.parse.FieldStatus

private val Amber = Color(0xFFE0B354)

@Composable
fun ConfirmationScreen(
    draft: DraftUi,
    online: Boolean,
    onField: (DraftFieldKey, String) -> Unit,
    onPickCandidate: (Contact) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title(draft.type), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Heard: “${draft.transcript}”",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (draft.type) {
            ActionType.SEND_EMAIL -> {
                DraftTextField("To", draft.recipient) { onField(DraftFieldKey.RECIPIENT, it) }
                if (draft.candidates.isNotEmpty()) {
                    Text(
                        "Did you mean:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Amber,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.candidates.forEach { c ->
                            SuggestionChip(
                                onClick = { onPickCandidate(c) },
                                label = { Text(c.name) },
                            )
                        }
                    }
                }
                draft.resolvedEmail?.let {
                    Text(
                        "Sends to $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DraftTextField("Subject", draft.subject) { onField(DraftFieldKey.SUBJECT, it) }
                DraftTextField("Body", draft.body, singleLine = false) {
                    onField(DraftFieldKey.BODY, it)
                }
            }

            ActionType.CREATE_EVENT -> {
                DraftTextField("Title", draft.subject) { onField(DraftFieldKey.SUBJECT, it) }
                DraftTextField("When", draft.datetime) { onField(DraftFieldKey.DATETIME, it) }
            }

            ActionType.SET_REMINDER -> {
                DraftTextField("Reminder", draft.body, singleLine = false) {
                    onField(DraftFieldKey.BODY, it)
                }
                DraftTextField("When", draft.datetime) { onField(DraftFieldKey.DATETIME, it) }
            }

            ActionType.FETCH_LAPTOP_FILE -> {
                DraftTextField("File to fetch", draft.pathHint) {
                    onField(DraftFieldKey.PATH_HINT, it)
                }
            }

            ActionType.GET_LAPTOP_CLIPBOARD -> {
                Text(
                    "Grabs whatever is on the laptop clipboard into a draft.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ActionType.CAPTURE_NOTE -> {
                DraftTextField("Note", draft.body, singleLine = false) {
                    onField(DraftFieldKey.BODY, it)
                }
            }
        }

        if (!online && draft.type in networkTypes) {
            Text(
                "Working offline — this will queue and send when connected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("Confirm")
            }
        }
    }
}

private val networkTypes = setOf(
    ActionType.SEND_EMAIL, ActionType.FETCH_LAPTOP_FILE, ActionType.GET_LAPTOP_CLIPBOARD,
)

private fun title(type: ActionType): String = when (type) {
    ActionType.SEND_EMAIL -> "Send email"
    ActionType.CREATE_EVENT -> "Add event"
    ActionType.SET_REMINDER -> "Set reminder"
    ActionType.FETCH_LAPTOP_FILE -> "Fetch file from laptop"
    ActionType.GET_LAPTOP_CLIPBOARD -> "Get laptop clipboard"
    ActionType.CAPTURE_NOTE -> "Save note"
}

@Composable
private fun DraftTextField(
    label: String,
    field: DraftField,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = field.text,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            colors = if (field.status == FieldStatus.NEEDS_INPUT) {
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = Amber,
                    focusedLabelColor = Amber,
                    unfocusedLabelColor = Amber,
                )
            } else {
                OutlinedTextFieldDefaults.colors()
            },
        )
        if (field.status == FieldStatus.NEEDS_INPUT) {
            Text(
                text = field.hint ?: "Check this",
                style = MaterialTheme.typography.bodySmall,
                color = Amber,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}
