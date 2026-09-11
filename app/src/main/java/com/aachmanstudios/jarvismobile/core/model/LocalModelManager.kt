package com.aachmanstudios.jarvismobile.core.model
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ModelState {
 data object Unloaded:ModelState
 data object Loading:ModelState
 data class Ready(val name:String,val context:Int):ModelState
 data class Error(val message:String):ModelState
}
class LocalModelManager {
 private val runtime=NativeLlamaRuntime()
 private val mutex=Mutex()
 private val _state=MutableStateFlow<ModelState>(ModelState.Unloaded)
 val state=_state.asStateFlow()
 suspend fun load(path:String,name:String,context:Int)=mutex.withLock {
 _state.value=ModelState.Loading
 try {runtime.load(path,context);_state.value=ModelState.Ready(name,context)}
 catch(e:CancellationException){withContext(NonCancellable){runtime.unload()};_state.value=ModelState.Unloaded;throw e}
 catch(e:Exception){_state.value=ModelState.Error(e.message?:"Load failed")}
 catch(e:UnsatisfiedLinkError){_state.value=ModelState.Error("Native library unavailable: "+e.message)}
 }
 suspend fun generate(turns:List<Turn>,maxTokens:Int,onText:(String)->Unit):String=mutex.withLock {
 check(_state.value is ModelState.Ready){"No local model loaded"}
 runtime.generate(turns,maxTokens,onText)
 }
 /** Called before launching the request; stop is never reset from the native worker. */
 fun prepareGeneration(){runtime.resetStop()}
 fun stop()=runtime.stop()
 suspend fun unload()=withContext(NonCancellable){stop();mutex.withLock{runtime.unload();_state.value=ModelState.Unloaded}}
}
