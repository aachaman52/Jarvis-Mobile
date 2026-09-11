package com.aachmanstudios.jarvismobile.core.tools
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.*

class ToolExecutor(private val context:Context,tools:List<JarvisTool>) {
 private val registry=tools.associateBy{it.name}.also{require(it.size==tools.size)}
 suspend fun execute(call:ToolCall):ToolResult=withContext(Dispatchers.IO) {
 try {
 val tool=registry[call.tool]?:return@withContext ToolResult("Unknown tool rejected: "+call.tool,false)
 tool.validate(call.arguments)
 tool.permission?.let{if(context.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED)return@withContext ToolResult("Permission required",false,it)}
 tool.execute(call.arguments)
 } catch(e:CancellationException){throw e}
 catch(e:Exception){ToolResult(e.message?:"Tool failed",false)}
 }
}
