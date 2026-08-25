package com.offhand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offhand.ui.HomeScreen
import com.offhand.ui.MainViewModel
import com.offhand.ui.OutboxScreen
import com.offhand.ui.theme.OffhandTheme

private enum class Screen { HOME, OUTBOX }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as OffhandApp).container
        setContent {
            OffhandTheme {
                OffhandRoot(container)
            }
        }
    }
}

@Composable
private fun OffhandRoot(container: AppContainer) {
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val online by viewModel.online.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.HOME,
                    onClick = { screen = Screen.HOME },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Home") },
                )
                NavigationBarItem(
                    selected = screen == Screen.OUTBOX,
                    onClick = { screen = Screen.OUTBOX },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("Outbox") },
                )
            }
        },
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(online = online, modifier = Modifier.padding(padding))
            Screen.OUTBOX -> OutboxScreen(actions = actions, modifier = Modifier.padding(padding))
        }
    }
}
