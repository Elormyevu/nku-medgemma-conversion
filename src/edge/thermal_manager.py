"""
Thermal Manager for Edge AI Devices
Manages device thermal state to prevent overheating during heavy AI inference.
Crucial for 'Edge of AI' prize on commodity hardware.

PRODUCTION NOTES:
- On Android, reads from /sys/class/power_supply/battery/temp
- On generic Linux, reads from thermal zones
- Falls back to mock values if sensors unavailable
"""

import time
import os
import logging
from typing import Optional, Dict, Any
from dataclasses import dataclass
from pathlib import Path

try:
    from ..config import THERMAL_THROTTLE_TEMP_C
except ImportError:
    # Fallback for direct execution
    THERMAL_THROTTLE_TEMP_C = 42.0

logger = logging.getLogger(__name__)


@dataclass
class ThermalStatus:
    """Thermal status result."""
    safe: bool
    temperature: float
    message: str
    cooldown_remaining: Optional[int] = None


class ThermalManager:
    """
    Manages device thermal state to prevent overheating during heavy AI inference.
    Supports Android, Linux, and fallback mock for development.
    """
    
    # Android thermal sensor paths
    ANDROID_BATTERY_TEMP = "/sys/class/power_supply/battery/temp"
    ANDROID_CPU_TEMP = "/sys/devices/virtual/thermal/thermal_zone0/temp"
    
    # Linux thermal zone base path
    LINUX_THERMAL_BASE = Path("/sys/class/thermal")
    
    # Cooldown configuration
    COOLDOWN_DURATION_SECONDS = 30
    
    def __init__(self, throttle_temp: float = None, enable_mock: bool = False):
        """
        Initialize thermal manager.
        
        Args:
            throttle_temp: Temperature threshold in Celsius (default from config)
            enable_mock: Force mock mode for testing
        """
        self.throttle_temp = throttle_temp or THERMAL_THROTTLE_TEMP_C
        self.enable_mock = enable_mock
        self.in_cooldown = False
        self.cooldown_start: Optional[float] = None
        
        # Detect platform and available sensors
        self._platform = self._detect_platform()
        self._sensor_path = self._find_sensor()
        
        logger.info(f"ThermalManager initialized: platform={self._platform}, "
                   f"sensor={'mock' if self._sensor_path is None else self._sensor_path}, "
                   f"throttle_temp={self.throttle_temp}°C")
    
    def _detect_platform(self) -> str:
        """Detect the running platform."""
        if self.enable_mock:
            return "mock"
        
        # Check for Android
        if os.path.exists(self.ANDROID_BATTERY_TEMP):
            return "android"
        
        # Check for Linux thermal zones
        if self.LINUX_THERMAL_BASE.exists():
            return "linux"
        
        return "mock"
    
    def _find_sensor(self) -> Optional[str]:
        """Find an available temperature sensor."""
        if self._platform == "mock":
            return None
        
        if self._platform == "android":
            # Prefer battery temp as it's more consistent
            if os.path.exists(self.ANDROID_BATTERY_TEMP):
                return self.ANDROID_BATTERY_TEMP
            if os.path.exists(self.ANDROID_CPU_TEMP):
                return self.ANDROID_CPU_TEMP
        
        if self._platform == "linux":
            # Find first available thermal zone
            for zone_dir in sorted(self.LINUX_THERMAL_BASE.glob("thermal_zone*")):
                temp_file = zone_dir / "temp"
                if temp_file.exists():
                    return str(temp_file)
        
        return None
    
    def get_temperature(self) -> float:
        """
        Read current device temperature.
        
        Returns:
            Temperature in Celsius
        """
        if self._sensor_path is None or self._platform == "mock":
            return self._get_mock_temperature()
        
        try:
            with open(self._sensor_path, 'r') as f:
                raw_value = int(f.read().strip())
            
            # Most Linux/Android sensors report in millidegrees
            if raw_value > 1000:
                return raw_value / 1000.0
            
            # Some sensors report in decidegrees (Android battery)
            if raw_value > 100:
                return raw_value / 10.0
            
            return float(raw_value)
            
        except (IOError, ValueError) as e:
            logger.warning(f"Failed to read temperature: {e}, falling back to mock")
            return self._get_mock_temperature()
    
    def _get_mock_temperature(self) -> float:
        """Generate mock temperature for development/testing."""
        import random
        
        # Simulate temperature between 35-45°C with some variation
        # In cooldown, simulate gradual cooling
        if self.in_cooldown and self.cooldown_start:
            elapsed = time.time() - self.cooldown_start
            cooling_factor = min(elapsed / self.COOLDOWN_DURATION_SECONDS, 1.0)
            base_temp = self.throttle_temp + 3.0 - (cooling_factor * 8.0)
        else:
            base_temp = 35.0
        
        jitter = random.uniform(-1.0, 3.0)
        return max(30.0, base_temp + jitter)
    
    def check_thermal_status(self) -> ThermalStatus:
        """
        Check if the device is safe for inference.
        
        Returns:
            ThermalStatus with safety flag, temperature, and message
        """
        current_temp = self.get_temperature()
        
        # Check if in cooldown period
        if self.in_cooldown:
            elapsed = time.time() - (self.cooldown_start or 0)
            remaining = int(self.COOLDOWN_DURATION_SECONDS - elapsed)
            
            if remaining <= 0:
                # Cooldown complete, check if temperature is safe now
                self.in_cooldown = False
                self.cooldown_start = None
                logger.info("Cooldown period ended")
            else:
                return ThermalStatus(
                    safe=False,
                    temperature=current_temp,
                    message=f"Device cooling down. Wait {remaining}s.",
                    cooldown_remaining=remaining
                )
        
        # Check temperature threshold
        if current_temp > self.throttle_temp:
            self.in_cooldown = True
            self.cooldown_start = time.time()
            logger.warning(f"Thermal limit exceeded: {current_temp:.1f}°C > {self.throttle_temp}°C")
            
            return ThermalStatus(
                safe=False,
                temperature=current_temp,
                message=f"Thermal limit exceeded ({current_temp:.1f}°C)! Cooldown active.",
                cooldown_remaining=self.COOLDOWN_DURATION_SECONDS
            )
        
        # Calculate headroom
        headroom = self.throttle_temp - current_temp
        
        if headroom < 5.0:
            message = f"Thermal status: warm ({current_temp:.1f}°C, {headroom:.1f}°C headroom)"
        else:
            message = f"Thermal status: normal ({current_temp:.1f}°C)"
        
        return ThermalStatus(
            safe=True,
            temperature=current_temp,
            message=message
        )
    
    def to_dict(self) -> Dict[str, Any]:
        """Convert current status to dictionary for API response."""
        status = self.check_thermal_status()
        return {
            "safe": status.safe,
            "temp": round(status.temperature, 1),
            "message": status.message,
            "cooldown_remaining": status.cooldown_remaining,
            "platform": self._platform,
            "throttle_threshold": self.throttle_temp
        }


def create_thermal_manager(enable_mock: bool = None) -> ThermalManager:
    """
    Factory function to create ThermalManager with appropriate configuration.
    
    Args:
        enable_mock: Force mock mode (None = auto-detect)
    
    Returns:
        Configured ThermalManager instance
    """
    # Check environment for mock flag
    if enable_mock is None:
        enable_mock = os.environ.get('ENABLE_MOCK_HARDWARE', 'false').lower() == 'true'
    
    return ThermalManager(enable_mock=enable_mock)


# Module-level singleton
_thermal_manager: Optional[ThermalManager] = None


def get_thermal_manager() -> ThermalManager:
    """Get or create the thermal manager singleton."""
    global _thermal_manager
    if _thermal_manager is None:
        _thermal_manager = create_thermal_manager()
    return _thermal_manager


if __name__ == "__main__":
    # Test the thermal manager
    logging.basicConfig(level=logging.INFO)
    
    tm = create_thermal_manager(enable_mock=True)
    
    print("Testing ThermalManager:")
    for i in range(5):
        status = tm.check_thermal_status()
        print(f"  [{i+1}] safe={status.safe}, temp={status.temperature:.1f}°C, msg={status.message}")
        
        if not status.safe:
            print(f"      Cooldown remaining: {status.cooldown_remaining}s")
        
        time.sleep(1)
