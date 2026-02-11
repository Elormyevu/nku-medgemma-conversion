class ResourceBudgetCalculator:
    """
    Mathematically estimates if 'Nku' will run on a specific chipset.
    Used to prove technical feasibility in the absence of physical devices.
    """
    
    def __init__(self):
        # Hardware Profiles (West Africa Common Devices)
        self.profiles = {
            "Tecno Spark 10C": {
                "chipset": "Unisoc T606",
                "ram_gb": 4,
                "npu_tops": 0.0, # No NPU
                "cpu_gflops": 110.0 # Approx
            },
            "Infinix Hot 30i": {
                "chipset": "Helio G37",
                "ram_gb": 4,
                "npu_tops": 0.0,
                "cpu_gflops": 95.0
            },
            "Samsun A14 (Entry)": {
                "chipset": "Exynos 850",
                "ram_gb": 4,
                "npu_tops": 0.0,
                "cpu_gflops": 120.0
            }
        }
        
        # Model Specs (PaliGemma 3B - INT4 Quantized)
        self.model_size_mb = 1800 # 1.8 GB
        self.model_ops_gflops = 45.0 # Estimated inference cost for one pass
        
        # OS Overhead
        self.android_overhead_mb = 1200 # Android 11+ takes ~1.2GB

    def check_viability(self, device_name):
        if device_name not in self.profiles:
            print(f"Device {device_name} not found.")
            return

        profile = self.profiles[device_name]
        print(f"--- Feasibility Check: {device_name} ({profile['chipset']}) ---")
        
        # 1. RAM Check
        total_ram = profile['ram_gb'] * 1024
        used_ram = self.android_overhead_mb + self.model_size_mb + 400 # 400MB safety buffer for UI/Camera
        
        print(f"RAM Budget: {used_ram}MB / {total_ram}MB")
        if used_ram > total_ram:
            print("[FAIL] Memory Overflow. App will crash.")
        else:
            percent = (used_ram / total_ram) * 100
            print(f"[PASS] Fits in RAM ({percent:.1f}% util).")

        # 2. Latency Estimation (CPU only)
        # Time = Operations / Speed
        # Theoretical max. Real world is usually 30-50% efficiency on mobile CPUs.
        efficiency_factor = 0.4 
        effective_gflops = profile['cpu_gflops'] * efficiency_factor
        
        estimated_time_sec = self.model_ops_gflops / effective_gflops
        
        print(f"Inference Latency: ~{estimated_time_sec:.2f} seconds")
        
        if estimated_time_sec > 10.0:
            print("[WARNING] User experience will be slow (>10s).")
        elif estimated_time_sec < 5.0:
             print("[PASS] Excellent responsiveness (<5s).")
        else:
             print("[ACCEPTABLE] Standard medical imaging wait time (5-10s).")
        print("\n")

if __name__ == "__main__":
    calc = ResourceBudgetCalculator()
    calc.check_viability("Tecno Spark 10C")
    calc.check_viability("Infinix Hot 30i")
