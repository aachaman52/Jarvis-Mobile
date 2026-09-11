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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aachmanstudios.jarvismobile.core.model.ModelState
import com.aachmanstudios.jarvismobile.core.speech.SpeechController
import com.aachmanstudios.jarvismobile.feature.settings.SettingsScreen
import com.aachmanstudios.jarvismobile.feature.debug.DebugScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChatScreen(vm:ChatViewModel=viewModel()) {
 val ui by vm.ui.collectAsState()
 val messages by vm.messages.collectAsState()
 val settings by vm.settings.collectAsState()
 val model by vm.modelState.collectAsState()
 val debug by vm.debug.collectAsState()
 val context=LocalContext.current
 var tab by rememberSaveable{mutableStateOf("Chat")}
 val currentPrivacy by rememberUpdatedState(settings.privacy)
 val speech=remember{SpeechController(context.applicationContext,vm::input,vm::status)}
 val owner=LocalLifecycleOwner.current
 DisposableEffect(owner){
 val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)speech.stopAll()}
 owner.lifecycle.addObserver(observer)
 onDispose{owner.lifecycle.removeObserver(observer);speech.close()}
 }
 LaunchedEffect(vm){vm.responses.collect{speech.speak(it,currentPrivacy)}}
 val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
 if(granted)speech.listen(currentPrivacy) else vm.status("Microphone permission denied. Text input still works.")
 }
 val toolPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.confirmPending(it)}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::importModel)}
 val listState=rememberLazyListState()
 LaunchedEffect(messages.size,ui.partial){if(messages.isNotEmpty())listState.animateScrollToItem(messages.size-1)}
 MaterialTheme {
 Scaffold(
 topBar={TopAppBar(title={Text("Jarvis")},actions={
 listOf("Chat","Settings","Debug").filter{it!="Debug"||settings.debug}.forEach{label->TextButton(onClick={tab=label}){Text(label)}}
 })},
 bottomBar={if(tab=="Chat")Column(Modifier.imePadding().padding(12.dp)){
 Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
 OutlinedTextField(ui.input,vm::input,Modifier.weight(1f),placeholder={Text("Message Jarvis")},maxLines=4,enabled=!ui.busy)
 TextButton(enabled=!ui.busy&&ui.pending==null,onClick={
 if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)speech.listen(settings.privacy)
 else micPermission.launch(Manifest.permission.RECORD_AUDIO)
 }){Text("Mic")}
 Button(enabled=ui.busy||ui.pending==null,onClick={if(ui.busy){speech.stopAll();vm.stop()}else{speech.stopAll();vm.send()}}){Text(if(ui.busy)"Stop" else "Send")}
 }
 Text(when(val s=model){
 ModelState.Unloaded->"Local AI • Not loaded • Tools available"
 ModelState.Loading->"Local AI • Loading"
 is ModelState.Ready->"Local AI • "+if(ui.busy)"Working" else "Ready"
 is ModelState.Error->"Local AI • "+s.message
 },style=MaterialTheme.typography.labelSmall)
 }}
 ){padding->
 Column(Modifier.fillMaxSize().padding(padding)){
 if(ui.status.isNotEmpty())Text(ui.status,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
 if(ui.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 when(tab){
 "Settings"->SettingsScreen(settings,model,ui.busy,vm::preference,{picker.launch(arrayOf("*/*"))},vm::load,vm::unload,vm.device())
 "Debug"->DebugScreen(debug)
 else->LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)){
 if(messages.isEmpty())item{Text("Ask a question, open an app, set a timer, or save a note. Load a GGUF model in Settings for offline AI answers.")}
 items(messages,key={it.id}){message->
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(message.role=="user")MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
 Column(Modifier.padding(12.dp)){Text(if(message.role=="user")"You" else "Jarvis",style=MaterialTheme.typography.labelMedium);Text(message.text)}
 }}
 if(ui.partial.isNotEmpty())item{Text(ui.partial,Modifier.padding(12.dp))}
 }
 }
 }
 }
 ui.pending?.let{pending->
 AlertDialog(onDismissRequest={vm.confirmPending(false)},title={Text(if(pending.cloudPrompt!=null)"Send to cloud?" else "Confirm Android action")},
 text={Text(when{
 pending.cloudPrompt!=null->"Only this request will leave the device:\n"+pending.cloudPrompt
 pending.permission!=null->"This action needs permission: "+pending.permission+"\n"+pending.call?.json()
 else->"The model proposed:\n"+pending.call?.json()+"\nNothing has executed yet."
 })},confirmButton={TextButton(onClick={pending.permission?.let{toolPermission.launch(it)}?:vm.confirmPending(true)}){Text("Allow")}},
 dismissButton={TextButton(onClick={vm.confirmPending(false)}){Text("Cancel")}})
 }
 }
}
