package com.aachmanstudios.jarvismobile.tools
import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.hardware.camera2.*
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.view.KeyEvent
import com.aachmanstudios.jarvismobile.core.tools.*
import com.aachmanstudios.jarvismobile.core.model.DeviceMonitor
import com.aachmanstudios.jarvismobile.data.repository.MemoryRepository
import java.net.URI

private fun Context.launch(intent:Intent):ToolResult {
 return try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ToolResult("Request sent to Android. Check the opened app to confirm.") }
 catch(e:ActivityNotFoundException){ToolResult("No installed app can handle this action.",false)}
}
class OpenAppTool(private val c:Context):JarvisTool {
 override val name="open_app"
 override fun validate(arguments:Map<String,Any>){arguments.fields(setOf("app"));arguments.text("app",120)}
 override suspend fun execute(arguments:Map<String,Any>):ToolResult {
 val name=arguments.text("app")
 val pm=c.packageManager
 val matches=pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
 .filter{it.loadLabel(pm).toString().equals(name,true)||it.activityInfo.packageName==name}.distinctBy{it.activityInfo.packageName}
 if(matches.isEmpty())return ToolResult("App not found. Use its exact launcher name.",false)
 if(matches.size>1)return ToolResult("Several apps match. Specify the package: "+matches.joinToString{it.activityInfo.packageName},false)
 val a=matches.single().activityInfo
 return c.launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(a.packageName,a.name)))
 }
}
// ToolExecutor checks CAMERA immediately before execution; SecurityException is still caught.
@SuppressLint("MissingPermission")
class FlashlightTool(private val c:Context):JarvisTool {
 override val name="flashlight"
 override val permission=Manifest.permission.CAMERA
 override fun validate(arguments:Map<String,Any>){arguments.fields(setOf("on"));require(arguments["on"] is Boolean){"on must be boolean"}}
 override suspend fun execute(arguments:Map<String,Any>):ToolResult {
 val cm=c.getSystemService(CameraManager::class.java)
 val id=cm.cameraIdList.firstOrNull{cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}
 ?:return ToolResult("No flashlight is available.",false)
 cm.setTorchMode(id,arguments["on"] as Boolean)
 return ToolResult("Flashlight "+if(arguments["on"]==true)"on" else "off")
 }
}
class AlarmTool(private val c:Context):JarvisTool {
 override val name="set_alarm"
 override fun validate(arguments:Map<String,Any>){
 arguments.fields(setOf("time"),setOf("label"));val t=arguments.text("time",5)
 require(Regex("([01][0-9]|2[0-3]):[0-5][0-9]").matches(t)){"Use a valid 24-hour HH:mm time"}
 if("label" in arguments)arguments.text("label",120)
 }
 override suspend fun execute(arguments:Map<String,Any>):ToolResult {
 val parts=arguments.text("time").split(":").map{it.toInt()}
 return c.launch(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,parts[0]).putExtra(AlarmClock.EXTRA_MINUTES,parts[1]).putExtra(AlarmClock.EXTRA_MESSAGE,arguments["label"] as? String?:"Jarvis").putExtra(AlarmClock.EXTRA_SKIP_UI,false))
 }
}
class TimerTool(private val c:Context):JarvisTool {
 override val name="set_timer"
 override fun validate(arguments:Map<String,Any>){
 arguments.fields(setOf("duration"));val d=arguments["duration"]
 require((d is Int||d is Long)&&(d as Number).toLong() in 1..86400){"duration must be integer seconds, 1–86400"}
 }
 override suspend fun execute(arguments:Map<String,Any>)=c.launch(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,(arguments["duration"] as Number).toInt()).putExtra(AlarmClock.EXTRA_MESSAGE,"Jarvis").putExtra(AlarmClock.EXTRA_SKIP_UI,true))
}
class OpenUrlTool(private val c:Context):JarvisTool {
 override val name="open_url"
 override fun validate(arguments:Map<String,Any>){
 arguments.fields(setOf("url"));val raw=arguments.text("url",2048);val u=URI(raw)
 require(u.scheme?.lowercase() in setOf("http","https")&&!u.host.isNullOrBlank()&&u.userInfo==null){"Only normal HTTP(S) URLs are allowed"}
 }
 override suspend fun execute(arguments:Map<String,Any>)=c.launch(Intent(Intent.ACTION_VIEW,Uri.parse(arguments.text("url",2048))))
}
class WebSearchTool(private val c:Context):JarvisTool {
 override val name="web_search"
 override fun validate(arguments:Map<String,Any>){arguments.fields(setOf("query"));arguments.text("query",1000)}
 override suspend fun execute(arguments:Map<String,Any>)=c.launch(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search").buildUpon().appendQueryParameter("q",arguments.text("query")).build()))
}
class CreateNoteTool(private val memory:MemoryRepository):JarvisTool {
 override val name="create_note"
 override fun validate(arguments:Map<String,Any>){arguments.fields(setOf("text"));arguments.text("text",4000)}
 override suspend fun execute(arguments:Map<String,Any>):ToolResult{memory.remember(arguments.text("text",4000));return ToolResult("Saved locally.")}
}
class ReadNotesTool(private val memory:MemoryRepository):JarvisTool {
 override val name="read_notes"
 override fun validate(arguments:Map<String,Any>){arguments.fields(emptySet())}
 override suspend fun execute(arguments:Map<String,Any>)=ToolResult(memory.notes())
}
class DeviceInfoTool(private val monitor:DeviceMonitor):JarvisTool {
 override val name="device_info"
 override fun validate(arguments:Map<String,Any>){arguments.fields(emptySet())}
 override suspend fun execute(arguments:Map<String,Any>):ToolResult{
 val d=monitor.snapshot();return ToolResult("RAM: ${d.profile.availableRamMb}/${d.profile.totalRamMb} MB available\nBattery: ${d.battery}%\nBattery temperature: ${d.batteryCelsius ?: "unknown"} °C (not CPU temperature)\nThermal status: ${d.thermal}\nFree storage: ${d.profile.freeStorageMb} MB\nAndroid API: ${d.profile.androidVersion}\nInternet: ${d.online}")
 }
}
class MediaTool(private val c:Context):JarvisTool {
 override val name="media_control"
 private val keys=mapOf("play" to KeyEvent.KEYCODE_MEDIA_PLAY,"pause" to KeyEvent.KEYCODE_MEDIA_PAUSE,"next" to KeyEvent.KEYCODE_MEDIA_NEXT,"previous" to KeyEvent.KEYCODE_MEDIA_PREVIOUS)
 override fun validate(arguments:Map<String,Any>){arguments.fields(setOf("action"));require(arguments.text("action") in keys){"Invalid media action"}}
 override suspend fun execute(arguments:Map<String,Any>):ToolResult {
 val am=c.getSystemService(AudioManager::class.java);val key=keys.getValue(arguments.text("action"))
 am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))
 return ToolResult("Media command sent. The active player may ignore it; Jarvis cannot force control of every app.")
 }
}
