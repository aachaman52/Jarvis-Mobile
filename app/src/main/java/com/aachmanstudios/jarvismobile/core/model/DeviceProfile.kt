package com.aachmanstudios.jarvismobile.core.model
import android.app.ActivityManager
import android.content.*
import android.net.*
import android.os.*

data class DeviceProfile(val totalRamMb:Long,val availableRamMb:Long,val cpuCores:Int,val androidVersion:Int,val freeStorageMb:Long)
enum class ModelTier { LITE,BALANCED,HIGH }
data class Recommendation(val tier:ModelTier,val description:String)
interface ModelRecommender { fun recommend(device:DeviceProfile):Recommendation }
class DefaultModelRecommender:ModelRecommender {
 override fun recommend(device:DeviceProfile)=when {
 device.totalRamMb<5000 -> Recommendation(ModelTier.LITE,"~1B Q4")
 device.totalRamMb<7000 -> Recommendation(ModelTier.BALANCED,"~1–2B Q4")
 device.totalRamMb<10500 -> Recommendation(ModelTier.BALANCED,"~2–3B Q4")
 else -> Recommendation(ModelTier.HIGH,"~3–4B Q4")
 }
}
data class DeviceState(val profile:DeviceProfile,val battery:Int,val thermal:Int,val batteryCelsius:Float?,val online:Boolean)
class DeviceMonitor(private val context:Context) {
 fun snapshot():DeviceState {
 val mi=ActivityManager.MemoryInfo()
 context.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
 val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
 val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
 val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,100)?:100
 val temp=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)
 val cm=context.getSystemService(ConnectivityManager::class.java)
 val net=cm.getNetworkCapabilities(cm.activeNetwork)
 return DeviceState(DeviceProfile(mi.totalMem/1048576,mi.availMem/1048576,Runtime.getRuntime().availableProcessors(),Build.VERSION.SDK_INT,context.filesDir.usableSpace/1048576),
 if(level<0||scale<=0)-1 else level*100/scale,context.getSystemService(PowerManager::class.java).currentThermalStatus,
 temp?.takeUnless{it==Int.MIN_VALUE}?.div(10f),net?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true)
 }
}
