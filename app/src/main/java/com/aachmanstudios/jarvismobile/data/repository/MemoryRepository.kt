package com.aachmanstudios.jarvismobile.data.repository
import com.aachmanstudios.jarvismobile.data.database.*
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
 val messages:Flow<List<Message>>
 suspend fun recent():List<Message>
 suspend fun append(role:String,text:String)
 suspend fun remember(text:String)
 suspend fun notes():String
}
class RoomMemory(private val dao:JarvisDao):MemoryRepository {
 override val messages=dao.messages()
 override suspend fun recent()=dao.recent(8).reversed()
 override suspend fun append(role:String,text:String){dao.message(Message(role=role,text=text.take(16000)));dao.prune()}
 override suspend fun remember(text:String){dao.note(Note(text=text))}
 override suspend fun notes()=dao.notes().joinToString("\n"){it.text}.ifEmpty{"No notes saved yet."}
}
data class Settings(val context:Int=2048,val maxTokens:Int=384,val tts:Boolean=false,val cloud:Boolean=false,val privacy:Boolean=true,val debug:Boolean=false,val modelPath:String="",val modelName:String="") {
 companion object {
 fun from(rows:List<Preference>):Settings{
 val m=rows.associate{it.key to it.value}
 return Settings(m["context"]?.toIntOrNull()?.coerceIn(1024,4096)?:2048,m["maxTokens"]?.toIntOrNull()?.coerceIn(64,512)?:384,m["tts"]=="true",m["cloud"]=="true",m["privacy"]!="false",m["debug"]=="true",m["modelPath"].orEmpty(),m["modelName"].orEmpty())
 }
 }
}
