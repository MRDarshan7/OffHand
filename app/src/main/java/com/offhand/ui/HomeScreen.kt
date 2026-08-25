package com.offhand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    online: Boolean,
    asrReady: Boolean,
    listening: Boolean,
    parsing: Boolean,
    parserStatus: String,
    partial: String,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onContactsClick: () -> Unit,
    onCameraClick: () -> Unit,
    onTitleLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ConnectivityBanner(online = online)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "OFFHAND",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, top = 8.dp)
                    .pointerInput(Unit) {
                        // Demo reset lives behind a long-press (M6).
                        detectTapGestures(onLongPress = { onTitleLongPress() })
                    },
            )
            IconButton(onClick = onCameraClick) {
                Text("📷", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onContactsClick) {
                Icon(Icons.Filled.Person, contentDescription = "Contacts")
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            if (listening) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onPressStart()
                                    try {
                                        awaitRelease()
                                    } finally {
                                        onPressEnd()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (listening) "Listening…" else "Hold to talk",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (listening) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = when {
                        parsing -> "Thinking…"
                        listening && partial.isNotBlank() -> partial
                        listening -> "…"
                        !asrReady -> "Preparing speech engine…"
                        else -> "Hold the button and speak"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }

        Text(
            text = parserStatus,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
        )
    }
}

@Composable
private fun ConnectivityBanner(online: Boolean) {
    // Offline is a normal state, never styled as an error.
    val background = if (online) Color(0xFF16342A) else Color(0xFF2A2A22)
    val foreground = if (online) Color(0xFF9BE8D5) else Color(0xFFD9D4B8)
    Surface(color = background, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (online) "Online" else "Working offline",
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
    }
}
