package com.aachmanstudios.jarvismobile.core.router
import com.aachmanstudios.jarvismobile.core.tools.ToolCall

data class RoutingContext(val availableRamMb:Long,val battery:Int,val thermal:Int,val online:Boolean,val privacy:Boolean,val cloudEnabled:Boolean,val cloudConfigured:Boolean,val localReady:Boolean)
enum class Path { TOOL,LOCAL,CLOUD_OFFER,UNAVAILABLE }
data class Decision(val path:Path,val reason:String,val call:ToolCall?=null)
class Router {
 fun route(request:String,c:RoutingContext):Decision {
 direct(request)?.let{return Decision(Path.TOOL,"Deterministic command",it)}
 val constrained=c.availableRamMb<600 || c.battery in 0..9 || c.thermal>=3
 val complex=Regex("(?i)\\b(very difficult|complex|advanced|prove|debug|programming|reasoning)\\b").containsMatchIn(request)
 val cloud=c.online&&!c.privacy&&c.cloudEnabled&&c.cloudConfigured
 if(cloud&&(complex||constrained||!c.localReady))return Decision(Path.CLOUD_OFFER,"Cloud requires approval for this request")
 if(!c.localReady)return Decision(Path.UNAVAILABLE,"Load a GGUF model. Direct Android commands still work.")
 if(constrained)return Decision(Path.UNAVAILABLE,"Local inference paused: low free RAM/battery or high thermal status.")
 return Decision(Path.LOCAL,if(complex)"Complex request; cloud unavailable or disabled" else "Local inference")
 }
 fun direct(raw:String):ToolCall? {
 val s=raw.trim().replace(Regex("(?i)^please\\s+"),"")
 fun call(name:String,vararg args:Pair<String,Any>)=ToolCall(name,mapOf(*args))
 Regex("(?i)^(?:turn|switch) (on|off) (?:the )?(?:flashlight|torch)[.!]?$").matchEntire(s)?.let{return call("flashlight","on" to (it.groupValues[1].lowercase()=="on"))}
 Regex("(?i)^(?:flashlight|torch) (on|off)[.!]?$").matchEntire(s)?.let{return call("flashlight","on" to (it.groupValues[1].lowercase()=="on"))}
 Regex("(?i)^(?:set |start )?(?:a )?timer (?:for )?(\\d+) (seconds?|minutes?|hours?)[.!]?$").matchEntire(s)?.let{
 val n=it.groupValues[1].toLongOrNull()?:return null
 val factor=when(it.groupValues[2].lowercase().first()){'h'->3600;'m'->60;else->1}
 if(n !in 1..86400)return null
 return call("set_timer","duration" to n*factor)
 }
 Regex("(?i)^set (?:an? )?alarm (?:for |at )?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?(?: (?:called|label) (.+))?$").matchEntire(s)?.let{
 var hour=it.groupValues[1].toInt();val minute=it.groupValues[2].ifEmpty{"0"}.toInt();val meridian=it.groupValues[3].lowercase()
 if(meridian.isNotEmpty()){if(hour !in 1..12)return null;hour=hour%12+if(meridian=="pm")12 else 0}
 return call("set_alarm","time" to "%02d:%02d".format(java.util.Locale.ROOT,hour,minute),"label" to it.groupValues[4].ifBlank{"Jarvis"})
 }
 Regex("(?i)^(?:remember(?: that)?|create (?:a )?note[:]?|note[:]) (.+)$",RegexOption.DOT_MATCHES_ALL).matchEntire(s)?.let{return call("create_note","text" to it.groupValues[1])}
 if(Regex("(?i)^(what did i ask you to remember|read(?: my)? notes|show(?: my)? notes)[?.!]?$").matches(s))return call("read_notes")
 if(Regex("(?i)^(device info|device information|phone info|battery status)[?.!]?$").matches(s))return call("device_info")
 Regex("(?i)^(play|pause|next|previous)(?: (?:music|media|track))?[.!]?$").matchEntire(s)?.let{return call("media_control","action" to it.groupValues[1].lowercase())}
 Regex("(?i)^(?:search(?: the web)?(?: for)?|web search) (.+)$").matchEntire(s)?.let{return call("web_search","query" to it.groupValues[1])}
 Regex("(?i)^open (.+)$").matchEntire(s)?.let{val t=it.groupValues[1];return if(t.startsWith("https://")||t.startsWith("http://"))call("open_url","url" to t) else call("open_app","app" to t)}
 return null
 }
}
