package com.offhand

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offhand.ui.ConfirmationScreen
import com.offhand.ui.ContactsDialog
import com.offhand.ui.HomeScreen
import com.offhand.ui.MainViewModel
import com.offhand.ui.OutboxScreen
import com.offhand.ui.Tab
import com.offhand.ui.theme.OffhandTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CALENDAR)
            add(Manifest.permission.WRITE_CALENDAR)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())

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
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val asrReady by viewModel.asrReady.collectAsStateWithLifecycle()
    val listening by viewModel.listening.collectAsStateWithLifecycle()
    val partial by viewModel.partial.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val ocrOpen by viewModel.ocrOpen.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var contactsOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (draft == null && !ocrOpen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.HOME,
                        onClick = { viewModel.selectTab(Tab.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.OUTBOX,
                        onClick = { viewModel.selectTab(Tab.OUTBOX) },
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                        },
                        label = { Text("Outbox") },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        val currentDraft = draft
        when {
            ocrOpen -> com.offhand.ui.OcrScreen(
                onCaptured = viewModel::onOcrPhoto,
                onClose = viewModel::closeOcr,
                modifier = contentModifier,
            )

            currentDraft != null -> ConfirmationScreen(
                draft = currentDraft,
                online = online,
                onField = viewModel::updateField,
                onPickCandidate = viewModel::pickCandidate,
                onConfirm = viewModel::confirmDraft,
                onCancel = viewModel::cancelDraft,
                modifier = contentModifier,
            )

            tab == Tab.HOME -> HomeScreen(
                online = online,
                asrReady = asrReady,
                listening = listening,
                parsing = viewModel.parsing.collectAsStateWithLifecycle().value,
                parserStatus = viewModel.parserStatus.collectAsStateWithLifecycle().value,
                partial = partial,
                onPressStart = viewModel::startPtt,
                onPressEnd = viewModel::stopPtt,
                onContactsClick = { contactsOpen = true },
                onCameraClick = viewModel::openOcr,
                onTitleLongPress = viewModel::resetDemoData,
                modifier = contentModifier,
            )

            else -> OutboxScreen(actions = actions, modifier = contentModifier)
        }

        if (contactsOpen) {
            ContactsDialog(
                contacts = contacts,
                onAdd = viewModel::addContact,
                onDelete = viewModel::deleteContact,
                onDismiss = { contactsOpen = false },
            )
        }
    }
}
