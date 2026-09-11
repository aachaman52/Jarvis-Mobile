package com.aachmanstudios.jarvismobile.core.model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface TokenSink { fun onBytes(bytes:ByteArray) }
/** Used only by the application-scoped manager, which serializes all operations. */
class NativeLlamaRuntime:LlamaRuntime {
 private var handle=0L
 private fun library(){NativeLibrary.ensure()}
 override suspend fun load(path:String,contextSize:Int)=withContext(Dispatchers.IO){
 library()
 if(handle!=0L){nativeFree(handle);handle=0L}
 handle=nativeLoad(path,contextSize)
 check(handle!=0L){"Model loading failed"}
 }
 override suspend fun generate(turns:List<Turn>,maxTokens:Int,onText:(String)->Unit):String=withContext(Dispatchers.Default){
 check(handle!=0L){"Load a model first"}
 val bytes=nativeGenerate(handle,turns.map{it.role}.toTypedArray(),turns.map{it.content.toByteArray(Charsets.UTF_8)}.toTypedArray(),maxTokens,TokenSink{onText(it.toString(Charsets.UTF_8))})
 bytes.toString(Charsets.UTF_8)
 }
 override suspend fun unload()=withContext(Dispatchers.IO){if(handle!=0L){nativeFree(handle);handle=0L}}
 fun resetStop(){library();nativeResetStop()}
 override fun stop(){if(NativeLibrary.loaded)nativeStop()}
 private external fun nativeLoad(path:String,contextSize:Int):Long
 private external fun nativeGenerate(handle:Long,roles:Array<String>,contents:Array<ByteArray>,maxTokens:Int,callback:TokenSink):ByteArray
 private external fun nativeFree(handle:Long)
 private external fun nativeResetStop()
 private external fun nativeStop()
}
private object NativeLibrary {
 @Volatile var loaded=false
 @Synchronized fun ensure(){if(!loaded){System.loadLibrary("jarvis_llama");loaded=true}}
}
