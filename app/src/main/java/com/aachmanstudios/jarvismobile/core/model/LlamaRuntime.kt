package com.aachmanstudios.jarvismobile.core.model
data class Turn(val role:String,val content:String)
interface LlamaRuntime {
 suspend fun load(path:String,contextSize:Int)
 suspend fun generate(turns:List<Turn>,maxTokens:Int,onText:(String)->Unit):String
 suspend fun unload()
 fun stop()
}
