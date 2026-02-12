package com.nku.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ThermalManager for Edge AI Devices
 * Manages device thermal state to prevent overheating during MedGemma inference.
 * Critical for "Edge of AI" prize on commodity hardware ($50 devices, 2GB RAM).
 */
data class ThermalStatus(
    val safe: Boolean,
    val temperatureCelsius: Float,
    val message: String,
    val cooldownRemainingSeconds: Int? = null
)

class ThermalManager(
    private val context: Context,
    private val throttleTemperature: Float = 42.0f,
    private val cooldownDurationSeconds: Int = 30
) {
    private val _thermalStatus = MutableStateFlow(ThermalStatus(true, 35.0f, "Initializing..."))
    val thermalStatus: StateFlow<ThermalStatus> = _thermalStatus.asStateFlow()
    
    private var inCooldown = false
    private var cooldownStartTime: Long = 0

    // PERF-1 fix: Cache temperature reads to avoid repeated sysfs I/O during camera streaming
    private var cachedTemperature: Float = 35.0f
    private var lastTempReadTime: Long = 0
    private val TEMP_CACHE_MS = 2000L  // 2-second cache
    
    /**
     * Get current device temperature (in °C).
     * P-3 fix: Uses the higher of battery temperature and CPU thermal zone
     * for more responsive detection (battery sensor can lag 10-30s behind CPU).
     * PERF-1 fix: Caches the result for 2 seconds to avoid repeated I/O.
     */
    fun getTemperature(): Float {
        val now = System.currentTimeMillis()
        if (now - lastTempReadTime < TEMP_CACHE_MS) {
            return cachedTemperature
        }

        val batteryTemp = try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 350) ?: 350
            tempTenths / 10.0f
        } catch (e: Exception) {
            35.0f
        }
        
        // P-3 fix: Read CPU thermal zone for more responsive temperature tracking
        val cpuTemp = try {
            val thermalDir = java.io.File("/sys/class/thermal/")
            if (thermalDir.exists()) {
                thermalDir.listFiles { f -> f.name.startsWith("thermal_zone") }
                    ?.mapNotNull { zone ->
                        try {
                            java.io.File(zone, "temp").readText().trim().toFloatOrNull()?.let { it / 1000f }
                        } catch (_: Exception) { null }
                    }?.maxOrNull()
            } else null
        } catch (_: Exception) { null }
        
        // Use the higher of battery and CPU temperatures
        val result = maxOf(batteryTemp, cpuTemp ?: batteryTemp)
        cachedTemperature = result
        lastTempReadTime = now
        return result
    }
    
    /**
     * Check if it's safe to run AI inference.
     * Returns ThermalStatus with safe flag and current temperature.
     */
    fun checkThermalStatus(): ThermalStatus {
        val currentTemp = getTemperature()
        
        // Check if still in cooldown
        if (inCooldown) {
            val elapsed = ((System.currentTimeMillis() - cooldownStartTime) / 1000).toInt()
            val remaining = cooldownDurationSeconds - elapsed
            
            if (remaining > 0) {
                val status = ThermalStatus(
                    safe = false,
                    temperatureCelsius = currentTemp,
                    message = "Cooling down: ${remaining}s remaining",
                    cooldownRemainingSeconds = remaining
                )
                _thermalStatus.value = status
                return status
            } else {
                inCooldown = false
            }
        }
        
        // Check current temperature
        val status = if (currentTemp > throttleTemperature) {
            inCooldown = true
            cooldownStartTime = System.currentTimeMillis()
            ThermalStatus(
                safe = false,
                temperatureCelsius = currentTemp,
                message = "Too hot: ${currentTemp.format(1)}°C - pausing inference",
                cooldownRemainingSeconds = cooldownDurationSeconds
            )
        } else {
            ThermalStatus(
                safe = true,
                temperatureCelsius = currentTemp,
                message = "OK: ${currentTemp.format(1)}°C"
            )
        }
        
        _thermalStatus.value = status
        return status
    }
    
    /**
     * Check if inference should proceed.
     * Call this before running MedGemma to prevent overheating.
     */
    fun canRunInference(): Boolean = checkThermalStatus().safe
    
    /**
     * Reset cooldown state (e.g., after device physically cooled).
     */
    fun reset() {
        inCooldown = false
        cooldownStartTime = 0
        _thermalStatus.value = ThermalStatus(true, getTemperature(), "Reset - ready")
    }
    
    private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
}
