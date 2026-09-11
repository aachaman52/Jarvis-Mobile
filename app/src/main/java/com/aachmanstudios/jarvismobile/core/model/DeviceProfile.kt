package com.aachmanstudios.jarvismobile.core.model
import android.app.ActivityManager
import android.content.*
import android.net.*
import android.os.*

data class DeviceProfile(val totalRamMb: Long, val availableRamMb: Long, val cpuCores: Int, val androidVersion: Int, val freeStorageMb: Long) {
    val totalRamGbText: String get() = "${(totalRamMb + 512) / 1024} GB"
    val freeStorageGbText: String get() = String.format(java.util.Locale.US, "%.1f GB", freeStorageMb.toDouble() / 1024.0)
}

enum class ModelTier { LITE, BALANCED, HIGH }

data class Recommendation(
    val tier: ModelTier,
    val model: ModelDefinition?,
    val description: String,
    val reason: String
)

interface ModelRecommender {
    fun recommend(device: DeviceProfile): Recommendation
}

class DefaultModelRecommender : ModelRecommender {
    override fun recommend(device: DeviceProfile): Recommendation {
        val fitting = ModelCatalog.models.filter { model ->
            device.totalRamMb >= model.minRamMb && (device.freeStorageMb * 1024 * 1024) >= model.requiredStorageBytes
        }
        val best = fitting.maxByOrNull { it.recommendedRamMb }
            ?: ModelCatalog.models.firstOrNull()

        return when {
            best == null -> Recommendation(ModelTier.LITE, null, "No model fits", "Insufficient storage or memory")
            best.tier == ModelTier.BALANCED -> Recommendation(
                tier = ModelTier.BALANCED,
                model = best,
                description = "${best.displayName} ${best.quantization} ~${best.sizeGbText}",
                reason = "Best balance of reasoning quality and speed for your device (${device.totalRamGbText} RAM, ${device.cpuCores} cores)."
            )
            best.tier == ModelTier.LITE -> Recommendation(
                tier = ModelTier.LITE,
                model = best,
                description = "${best.displayName} ${best.quantization} ~${best.sizeGbText}",
                reason = "Lightweight footprint optimized for smooth execution on your hardware."
            )
            else -> Recommendation(
                tier = ModelTier.HIGH,
                model = best,
                description = "${best.displayName} ${best.quantization} ~${best.sizeGbText}",
                reason = "High parameter model recommended for power hardware."
            )
        }
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
