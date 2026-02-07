import torch
import torchaudio
from transformers import AutoModel, AutoFeatureExtractor
import sys
import os
# Define model path - assumes download_hear.sh has been run
MODEL_PATH = "models/hear"
def load_model():
    """Loads the HeAR model from local path."""
    if not os.path.exists(MODEL_PATH):
        print(f"Error: Model path '{MODEL_PATH}' not found. Run download_hear.sh first.")
        # Fallback to direct HF load if local not found (for testing)
        print("Attempting to load directly from Hugging Face (google/hear)...")
        # SECURITY: Do NOT use trust_remote_code=True — allows arbitrary code execution
        # If the model requires custom code, vendor it locally first
        return AutoModel.from_pretrained("google/hear", trust_remote_code=False)
    
    print(f"Loading model from {MODEL_PATH}...")
    try:
        model = AutoModel.from_pretrained(MODEL_PATH, trust_remote_code=False)
        return model
    except Exception as e:
        print(f"Failed to load model: {e}")
        return None
def preprocess_audio(audio_path):
    """
    Loads audio and resamples to 16kHz mono.
    Returns tensor ready for model input.
    """
    try:
        waveform, sample_rate = torchaudio.load(audio_path)
        
        # Convert to mono if stereo
        if waveform.shape[0] > 1:
            waveform = torch.mean(waveform, dim=0, keepdim=True)
        
        # Resample to 16kHz
        if sample_rate != 16000:
            resampler = torchaudio.transforms.Resample(sample_rate, 16000)
            waveform = resampler(waveform)
            
        print(f"Processed audio shape: {waveform.shape}")
        return waveform
    except Exception as e:
        print(f"Error processing audio: {e}")
        return None
def run_inference(model, audio_tensor):
    """Runs the model on the audio tensor."""
    if model is None or audio_tensor is None:
        return
    
    print("Running inference...")
    with torch.no_grad():
        # Note: Actual forward pass depends on specific model signature.
        outputs = model(audio_tensor)
        
    print("Inference complete.")
    if hasattr(outputs, 'last_hidden_state'):
         print(f"Embedding shape: {outputs.last_hidden_state.shape}")
         print(f"First 5 values: {outputs.last_hidden_state[0, :5]}")
    else:
        print(f"Output keys: {outputs.keys()}")
if __name__ == "__main__":
    audio_file = "dummy_input.wav"
    if len(sys.argv) > 1:
        audio_file = sys.argv[1]
        
    print(f"Target Audio: {audio_file}")
    
    model = load_model()
    audio_tensor = preprocess_audio(audio_file)
    run_inference(model, audio_tensor)
