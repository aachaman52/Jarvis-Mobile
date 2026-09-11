package com.aachmanstudios.jarvismobile.feature.debug
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun DebugScreen(d:DebugEntry){
 SelectionContainer{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 Text("Last request · in-memory only",style=MaterialTheme.typography.titleLarge)
 listOf("Request" to d.request,"Router decision" to d.decision,"Selected path" to d.path,"Tool call" to d.toolCall,"Execution result" to d.result,"Elapsed" to "${d.elapsedMs} ms","Available RAM at request" to "${d.ramMb} MB").forEach{(label,value)->Text(label,style=MaterialTheme.typography.labelLarge);Text(value.ifEmpty{"—"});HorizontalDivider()}
 }}
}
