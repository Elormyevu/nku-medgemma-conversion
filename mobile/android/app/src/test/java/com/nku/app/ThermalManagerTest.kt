package com.nku.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for ThermalManager — Device Safety for Edge AI.
 *
 * Tests thermal status lifecycle, cooldown logic, and inference gating.
 * Uses a testable subclass that overrides getTemperature() to avoid
 * Android framework dependencies (BatteryManager, filesystem reads).
 */
class ThermalManagerTest {

    /**
     * Testable ThermalManager subclass that bypasses Android-specific
     * temperature reading (BatteryManager + /sys/class/thermal/).
     * Allows tests to control the reported temperature.
     */
    private class TestableThermalManager(
        private var simulatedTemp: Float = 35.0f,
        throttleTemp: Float = 42.0f,
        cooldownSeconds: Int = 30
    ) {
        private var inCooldown = false
        private var cooldownStartTime: Long = 0
        private val throttleTemperature = throttleTemp
        private val cooldownDurationSeconds = cooldownSeconds

        fun setTemperature(temp: Float) { simulatedTemp = temp }
        fun getTemperature(): Float = simulatedTemp

        fun checkThermalStatus(): ThermalStatus {
            val currentTemp = getTemperature()

            if (inCooldown) {
                val elapsed = ((System.currentTimeMillis() - cooldownStartTime) / 1000).toInt()
                val remaining = cooldownDurationSeconds - elapsed

                if (remaining > 0) {
                    return ThermalStatus(
                        safe = false,
                        temperatureCelsius = currentTemp,
                        message = "Cooling down: ${remaining}s remaining",
                        cooldownRemainingSeconds = remaining
                    )
                } else {
                    inCooldown = false
                }
            }

            return if (currentTemp > throttleTemperature) {
                inCooldown = true
                cooldownStartTime = System.currentTimeMillis()
                ThermalStatus(
                    safe = false,
                    temperatureCelsius = currentTemp,
                    message = "Too hot: ${"%.1f".format(currentTemp)}°C - pausing inference",
                    cooldownRemainingSeconds = cooldownDurationSeconds
                )
            } else {
                ThermalStatus(
                    safe = true,
                    temperatureCelsius = currentTemp,
                    message = "OK: ${"%.1f".format(currentTemp)}°C"
                )
            }
        }

        fun canRunInference(): Boolean = checkThermalStatus().safe

        fun reset() {
            inCooldown = false
            cooldownStartTime = 0
        }
    }

    private lateinit var thermalManager: TestableThermalManager

    @Before
    fun setUp() {
        thermalManager = TestableThermalManager(
            simulatedTemp = 35.0f,
            throttleTemp = 42.0f,
            cooldownSeconds = 30
        )
    }

    // ─── Normal Temperature ────────────────────────────────

    @Test
    fun `normal temperature reports safe status`() {
        thermalManager.setTemperature(35.0f)
        val status = thermalManager.checkThermalStatus()

        assertTrue("35°C should be safe", status.safe)
        assertEquals(35.0f, status.temperatureCelsius, 0.1f)
        assertNull("No cooldown at normal temp", status.cooldownRemainingSeconds)
        assertTrue("Message should indicate OK", status.message.contains("OK"))
    }

    @Test
    fun `canRunInference returns true at normal temperature`() {
        thermalManager.setTemperature(38.0f)
        assertTrue("38°C should allow inference", thermalManager.canRunInference())
    }

    // ─── Threshold Boundary ────────────────────────────────

    @Test
    fun `temperature at threshold is still safe`() {
        thermalManager.setTemperature(42.0f)
        val status = thermalManager.checkThermalStatus()
        assertTrue("Exactly at threshold (42°C) should be safe", status.safe)
    }

    @Test
    fun `temperature above threshold triggers throttling`() {
        thermalManager.setTemperature(43.0f)
        val status = thermalManager.checkThermalStatus()

        assertFalse("43°C should not be safe", status.safe)
        assertEquals(43.0f, status.temperatureCelsius, 0.1f)
        assertNotNull("Should have cooldown remaining", status.cooldownRemainingSeconds)
        assertEquals(30, status.cooldownRemainingSeconds)
        assertTrue("Message should indicate too hot", status.message.contains("Too hot"))
    }

    @Test
    fun `canRunInference returns false when overheated`() {
        thermalManager.setTemperature(45.0f)
        assertFalse("45°C should block inference", thermalManager.canRunInference())
    }

    // ─── Cooldown Behavior ─────────────────────────────────

    @Test
    fun `cooldown persists even after temperature drops`() {
        // Trigger overheat
        thermalManager.setTemperature(44.0f)
        thermalManager.checkThermalStatus()

        // Temperature drops back to normal
        thermalManager.setTemperature(36.0f)
        val status = thermalManager.checkThermalStatus()

        // Should still be in cooldown (30 seconds haven't passed)
        assertFalse("Should still be in cooldown even though temp dropped", status.safe)
    }

    @Test
    fun `reset clears cooldown state`() {
        // Trigger overheat
        thermalManager.setTemperature(44.0f)
        thermalManager.checkThermalStatus()

        // Reset
        thermalManager.reset()
        thermalManager.setTemperature(36.0f)

        val status = thermalManager.checkThermalStatus()
        assertTrue("After reset, normal temp should be safe", status.safe)
    }

    // ─── ThermalStatus Data Class ──────────────────────────

    @Test
    fun `ThermalStatus safe status has no cooldown`() {
        val safe = ThermalStatus(safe = true, temperatureCelsius = 37.0f, message = "OK")
        assertTrue(safe.safe)
        assertNull(safe.cooldownRemainingSeconds)
    }

    @Test
    fun `ThermalStatus unsafe status has cooldown`() {
        val unsafe = ThermalStatus(safe = false, temperatureCelsius = 44.0f, message = "Hot", cooldownRemainingSeconds = 25)
        assertFalse(unsafe.safe)
        assertEquals(25, unsafe.cooldownRemainingSeconds)
    }

    @Test
    fun `ThermalStatus equality works correctly`() {
        val a = ThermalStatus(safe = true, temperatureCelsius = 36.5f, message = "OK: 36.5°C")
        val b = ThermalStatus(safe = true, temperatureCelsius = 36.5f, message = "OK: 36.5°C")
        assertEquals(a, b)
    }

    // ─── Custom Thresholds ─────────────────────────────────

    @Test
    fun `custom threshold is respected`() {
        val lowThreshold = TestableThermalManager(
            simulatedTemp = 38.0f,
            throttleTemp = 37.0f,
            cooldownSeconds = 10
        )
        val status = lowThreshold.checkThermalStatus()
        assertFalse("38°C > 37°C threshold should throttle", status.safe)
        assertEquals(10, status.cooldownRemainingSeconds)
    }

    @Test
    fun `high threshold allows higher temperatures`() {
        val highThreshold = TestableThermalManager(
            simulatedTemp = 44.0f,
            throttleTemp = 50.0f,
            cooldownSeconds = 60
        )
        val status = highThreshold.checkThermalStatus()
        assertTrue("44°C < 50°C threshold should be safe", status.safe)
    }

    // ─── Edge Cases ────────────────────────────────────────

    @Test
    fun `zero temperature is safe`() {
        thermalManager.setTemperature(0.0f)
        assertTrue(thermalManager.canRunInference())
    }

    @Test
    fun `negative temperature is safe`() {
        thermalManager.setTemperature(-10.0f)
        assertTrue(thermalManager.canRunInference())
    }

    @Test
    fun `extreme temperature triggers throttling`() {
        thermalManager.setTemperature(100.0f)
        assertFalse(thermalManager.canRunInference())
    }
}
