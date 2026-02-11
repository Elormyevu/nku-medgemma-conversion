import numpy as np
import scipy.io.wavfile as wav
def generate_sine_wave(filename, duration=5, sample_rate=16000, frequency=440):
    """Generates a sine wave audio file (16kHz mono)."""
    t = np.linspace(0, duration, int(sample_rate * duration), endpoint=False)
    audio = 0.5 * np.sin(2 * np.pi * frequency * t)
    # Convert to 16-bit PCM
    audio_int16 = (audio * 32767).astype(np.int16)
    wav.write(filename, sample_rate, audio_int16)
    print(f"Generated {filename} ({duration}s, {sample_rate}Hz, Mono)")
if __name__ == "__main__":
    generate_sine_wave("dummy_input.wav")
