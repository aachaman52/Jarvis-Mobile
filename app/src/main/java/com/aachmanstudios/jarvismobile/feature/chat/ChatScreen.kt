package com.aachmanstudios.jarvismobile.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aachmanstudios.jarvismobile.core.model.DefaultModelRecommender
import com.aachmanstudios.jarvismobile.core.model.ModelState
import com.aachmanstudios.jarvismobile.core.speech.SpeechController
import com.aachmanstudios.jarvismobile.feature.debug.DebugScreen
import com.aachmanstudios.jarvismobile.feature.models.ModelManagerScreen
import com.aachmanstudios.jarvismobile.feature.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val messages by vm.messages.collectAsState()
    val settings by vm.settings.collectAsState()
    val model by vm.modelState.collectAsState()
    val debug by vm.debug.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("Chat") }
    val currentPrivacy by rememberUpdatedState(settings.privacy)
    val speech = remember { SpeechController(context.applicationContext, vm::input, vm::status) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) speech.stopAll()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            speech.close()
        }
    }

    LaunchedEffect(vm) {
        vm.responses.collect { speech.speak(it, currentPrivacy) }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speech.listen(currentPrivacy) else vm.status("Microphone permission denied. Text input still works.")
    }

    val toolPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.confirmPending(it)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, ui.partial) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val deviceState = remember { vm.device() }
    val recommendation = remember { DefaultModelRecommender().recommend(deviceState.profile) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Jarvis") },
                    actions = {
                        listOf("Chat", "Models", "Settings", "Debug")
                            .filter { it != "Debug" || settings.debug }
                            .forEach { label ->
                                val isSelected = tab == label
                                TextButton(
                                    onClick = { tab = label },
                                    colors = if (isSelected) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.textButtonColors()
                                ) {
                                    Text(
                                        label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                    }
                )
            },
            bottomBar = {
                if (tab == "Chat") {
                    Column(Modifier.imePadding().padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = ui.input,
                                onValueChange = vm::input,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Message Jarvis") },
                                maxLines = 4,
                                enabled = !ui.busy
                            )
                            TextButton(
                                enabled = !ui.busy && ui.pending == null,
                                onClick = {
                                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        speech.listen(settings.privacy)
                                    } else {
                                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            ) {
                                Text("Mic")
                            }
                            Button(
                                enabled = ui.busy || ui.pending == null,
                                onClick = {
                                    if (ui.busy) {
                                        speech.stopAll()
                                        vm.stop()
                                    } else {
                                        speech.stopAll()
                                        vm.send()
                                    }
                                }
                            ) {
                                Text(if (ui.busy) "Stop" else "Send")
                            }
                        }
                        Text(
                            text = when (val s = model) {
                                ModelState.Unloaded -> "Local AI • Offline Model Not Loaded • Tools active"
                                ModelState.Loading -> "Local AI • Loading Model into RAM…"
                                is ModelState.Ready -> "Local AI Ready • ${s.name} (${s.context} ctx)"
                                is ModelState.Error -> "Local AI • " + s.message
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (ui.status.isNotEmpty()) {
                    Text(ui.status, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (ui.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                when (tab) {
                    "Models" -> ModelManagerScreen(onNavigateToChat = { tab = "Chat" })
                    "Settings" -> SettingsScreen(
                        s = settings,
                        model = model,
                        busy = ui.busy,
                        save = vm::preference,
                        onOpenModelManager = { tab = "Models" },
                        device = vm.device()
                    )
                    "Debug" -> DebugScreen(debug)
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (model !is ModelState.Ready) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Device detected: ${deviceState.profile.totalRamGbText} RAM • ${deviceState.profile.cpuCores} CPU cores",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = "Recommended: ${recommendation.model?.displayName ?: "Qwen2.5 3B"} (${recommendation.model?.sizeGbText ?: "~2.1 GB"})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(recommendation.reason, style = MaterialTheme.typography.bodySmall)
                                        Button(onClick = { tab = "Models" }) {
                                            Text("Download Model in Model Manager")
                                        }
                                    }
                                }
                            }
                        }
                        if (messages.isEmpty()) {
                            item {
                                Text("Ask a question, open an app, set a timer, or save a note. Offline AI reasoning is powered by local GGUF models.")
                            }
                        }
                        items(messages, key = { it.id }) { message ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        if (message.role == "user") "You" else "Jarvis",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(message.text)
                                }
                            }
                        }
                        if (ui.partial.isNotEmpty()) {
                            item { Text(ui.partial, Modifier.padding(12.dp)) }
                        }
                    }
                }
            }
        }

        ui.pending?.let { pending ->
            AlertDialog(
                onDismissRequest = { vm.confirmPending(false) },
                title = { Text(if (pending.cloudPrompt != null) "Send to cloud?" else "Confirm Android action") },
                text = {
                    Text(
                        when {
                            pending.cloudPrompt != null -> "Only this request will leave the device:\n" + pending.cloudPrompt
                            pending.permission != null -> "This action needs permission: " + pending.permission + "\n" + pending.call?.json()
                            else -> "The model proposed:\n" + pending.call?.json() + "\nNothing has executed yet."
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        pending.permission?.let { toolPermission.launch(it) } ?: vm.confirmPending(true)
                    }) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.confirmPending(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
