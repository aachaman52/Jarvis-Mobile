package com.aachmanstudios.jarvismobile.core.tools
import org.json.JSONObject
import org.json.JSONTokener

data class ToolCall(val tool:String,val arguments:Map<String,Any>) {
 fun json()=JSONObject().put("type","tool").put("tool",tool).put("arguments",JSONObject(arguments)).toString()
}
data class ToolResult(val text:String,val success:Boolean=true,val requiredPermission:String?=null)
interface JarvisTool {
 val name:String
 val permission:String? get()=null
 fun validate(arguments:Map<String,Any>)
 suspend fun execute(arguments:Map<String,Any>):ToolResult
}
object ToolParser {
 fun parse(raw:String):ToolCall {
 require(raw.length<=16000){"Tool output is too large"}
 val tokenizer=JSONTokener(raw.trim())
 val j=tokenizer.nextValue() as? JSONObject ?: error("Tool output must be a JSON object")
 require(tokenizer.nextClean()=='\u0000'){"Trailing content is not allowed"}
 require(j.keys().asSequence().toSet()==setOf("type","tool","arguments")){"Unexpected tool fields"}
 require(j.get("type")=="tool"){"Not a tool call"}
 val name=j.get("tool");require(name is String){"Tool name must be text"}
 val args=j.getJSONObject("arguments")
 return ToolCall(name,args.keys().asSequence().associateWith{args.get(it)})
 }
}
fun Map<String,Any>.fields(required:Set<String>,optional:Set<String> = emptySet()) {
 require(keys.containsAll(required)&&keys.all{it in required||it in optional}){"Invalid argument fields"}
}
fun Map<String,Any>.text(key:String,max:Int=1000):String {
 val v=this[key];require(v is String&&v.isNotBlank()&&v.length<=max){"Invalid $key"};return v
}
