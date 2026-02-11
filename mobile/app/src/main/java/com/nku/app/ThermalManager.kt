package com.nku.app

import android.content.Context
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
    
    /**
     * Get current device temperature from battery sensor.
     * Android provides battery temperature in tenths of degrees Celsius.
     */
    fun getTemperature(): Float {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val tempTenths = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) ?: 350
            tempTenths / 10.0f
        } catch (e: Exception) {
            // Fallback to safe default if sensor unavailable
            35.0f
        }
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
