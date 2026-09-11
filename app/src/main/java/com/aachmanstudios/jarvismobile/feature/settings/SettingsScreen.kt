package com.aachmanstudios.jarvismobile.feature.settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.aachmanstudios.jarvismobile.core.model.*
import com.aachmanstudios.jarvismobile.data.repository.Settings

@Composable fun SettingsScreen(s:Settings,model:ModelState,busy:Boolean,save:(String,String)->Unit,select:()->Unit,load:()->Unit,unload:()->Unit,device:DeviceState){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text("Local model",style=MaterialTheme.typography.titleLarge)
 Text(s.modelName.ifBlank{"No GGUF selected"})
 Text(when(model){ModelState.Unloaded->"Unloaded";ModelState.Loading->"Loading…";is ModelState.Ready->"Loaded · ${model.context} context";is ModelState.Error->model.message})
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
 Button(onClick=select,enabled=!busy){Text("Select GGUF")}
 Button(onClick=load,enabled=!busy&&s.modelPath.isNotEmpty()){Text("Load")}
 TextButton(onClick=unload,enabled=!busy){Text("Unload")}
 }
 Text("Context size (changing it unloads the model)")
 Row{listOf(1024,2048,4096).forEach{n->FilterChip(selected=s.context==n,onClick={save("context",n.toString())},enabled=!busy,label={Text(n.toString())});Spacer(Modifier.width(8.dp))}}
 Text("Maximum output tokens")
 Row{listOf(256,384,512).forEach{n->FilterChip(selected=s.maxTokens==n,onClick={save("maxTokens",n.toString())},enabled=!busy,label={Text(n.toString())});Spacer(Modifier.width(8.dp))}}
 Toggle("Speak responses",s.tts,!busy){save("tts",it.toString())}
 Toggle("Cloud fallback",s.cloud,!busy){save("cloud",it.toString())}
 Text("Cloud provider interface is ready; none is configured. This toggle alone sends nothing.",style=MaterialTheme.typography.bodySmall)
 Toggle("Privacy mode",s.privacy,!busy){save("privacy",it.toString())}
 Text("Privacy mode blocks cloud inference and requires on-device speech/offline TTS. Explicit browser searches and URLs still open your browser.",style=MaterialTheme.typography.bodySmall)
 Toggle("Debug router",s.debug,!busy){save("debug",it.toString())}
 HorizontalDivider()
 Text("Device",style=MaterialTheme.typography.titleLarge)
 Text("Available RAM: ${device.profile.availableRamMb} / ${device.profile.totalRamMb} MB\nBattery: ${device.battery}%\nThermal status: ${device.thermal}\nFree storage: ${device.profile.freeStorageMb} MB")
 val recommendation=DefaultModelRecommender().recommend(device.profile)
 Text("Suggested tier: ${recommendation.tier} · ${recommendation.description}")
 Text("Choose an instruction-tuned Q4 GGUF with an embedded chat template. Only one model is kept. Import copies the file into private storage; keep sufficient free space.",style=MaterialTheme.typography.bodySmall)
 }
}
@Composable private fun Toggle(label:String,value:Boolean,enabled:Boolean,change:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked=value,onCheckedChange=change,enabled=enabled)}
}
