package com.aachmanstudios.jarvismobile
import android.app.Application
import android.content.ComponentCallbacks2
import androidx.room.Room
import com.aachmanstudios.jarvismobile.cloud.CloudRegistry
import com.aachmanstudios.jarvismobile.core.model.*
import com.aachmanstudios.jarvismobile.core.tools.ToolExecutor
import com.aachmanstudios.jarvismobile.data.database.JarvisDatabase
import com.aachmanstudios.jarvismobile.data.repository.RoomMemory
import com.aachmanstudios.jarvismobile.data.repository.ModelRepository
import com.aachmanstudios.jarvismobile.tools.*
import kotlinx.coroutines.*

class JarvisApplication:Application() {
 val appScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 val db by lazy{Room.databaseBuilder(this,JarvisDatabase::class.java,"jarvis.db").build()}
 val memory by lazy{RoomMemory(db.dao())}
    val monitor by lazy { DeviceMonitor(this) }
    val model by lazy { LocalModelManager() }
    val downloadManager by lazy { ModelDownloadManager(this, appScope) }
    val modelRepo by lazy { ModelRepository(this, db.dao(), downloadManager) }
    val cloud = CloudRegistry()
    val tools by lazy { ToolExecutor(this, listOf(OpenAppTool(this), FlashlightTool(this), AlarmTool(this), TimerTool(this), OpenUrlTool(this), WebSearchTool(this), CreateNoteTool(memory), ReadNotesTool(memory), DeviceInfoTool(monitor), MediaTool(this))) }
 override fun onTrimMemory(level:Int){
 super.onTrimMemory(level)
 if(level==ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL||level==ComponentCallbacks2.TRIM_MEMORY_COMPLETE){model.stop();appScope.launch{model.unload()}}
 }
}
