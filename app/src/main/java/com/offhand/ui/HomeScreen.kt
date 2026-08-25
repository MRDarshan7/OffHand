package com.offhand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(online: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ConnectivityBanner(online = online)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PushToTalkButton()
        }
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

@Composable
private fun PushToTalkButton() {
    var pressed by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    if (pressed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            try {
                                awaitRelease()
                            } finally {
                                pressed = false
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (pressed) "Listening…" else "Hold to talk",
                style = MaterialTheme.typography.titleMedium,
                color = if (pressed) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Voice capture arrives in Milestone 2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
