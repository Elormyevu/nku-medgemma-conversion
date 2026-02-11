#!/usr/bin/env python3
"""
Nku Model Chain Integration Test - V3 (Official TranslateGemma Format)

Uses the OFFICIAL TranslateGemma prompt template:
  "You are a professional {source_name} ({source_lang}) to {target_name} ({target_lang}) translator..."

Requirements:
  pip install llama-cpp-python huggingface_hub

Usage:
  python test_model_chain_v3.py
"""

import os
import sys
from pathlib import Path

try:
    from llama_cpp import Llama
    from huggingface_hub import hf_hub_download
except ImportError:
    print("Install dependencies: pip install llama-cpp-python huggingface_hub")
    sys.exit(1)


# --- Configuration ---
MEDGEMMA_REPO = "wredd/medgemma-4b-gguf"
MEDGEMMA_FILE = "medgemma-4b-q2_k.gguf"

TRANSLATEGEMMA_REPO = "wredd/translategemma-4b-gguf"
TRANSLATEGEMMA_FILE = "translategemma-4b-q2_k.gguf"

MODELS_DIR = Path.home() / ".cache" / "nku_models"

# Language mappings for TranslateGemma
LANGUAGES = {
    "twi": {"name": "Twi", "code": "ak"},  # Akan/Twi uses 'ak' in BCP 47
    "english": {"name": "English", "code": "en"},
    "yoruba": {"name": "Yoruba", "code": "yo"},
    "hausa": {"name": "Hausa", "code": "ha"},
    "swahili": {"name": "Swahili", "code": "sw"},
}


def download_model(repo_id: str, filename: str) -> Path:
    """Download model from HuggingFace if not cached."""
    print(f"[*] Checking for {filename}...")
    model_path = MODELS_DIR / filename
    
    if model_path.exists():
        print(f"    ✓ Found cached: {model_path}")
        return model_path
    
    print(f"    Downloading from {repo_id}...")
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    
    downloaded = hf_hub_download(
        repo_id=repo_id,
        filename=filename,
        local_dir=MODELS_DIR,
        local_dir_use_symlinks=False
    )
    print(f"    ✓ Downloaded: {downloaded}")
    return Path(downloaded)


def load_model(model_path: Path, context_size: int = 2048) -> Llama:
    """Load a GGUF model with minimal memory settings."""
    print(f"[*] Loading {model_path.name}...")
    
    model = Llama(
        model_path=str(model_path),
        n_ctx=context_size,
        n_batch=512,
        n_threads=4,
        verbose=False
    )
    print(f"    ✓ Model loaded")
    return model


def translate_official(model: Llama, text: str, source_lang: str, target_lang: str) -> str:
    """
    Translate using OFFICIAL TranslateGemma prompt format.
    Reference: https://huggingface.co/google/translategemma-4b
    """
    src = LANGUAGES.get(source_lang.lower(), {"name": source_lang, "code": source_lang[:2]})
    tgt = LANGUAGES.get(target_lang.lower(), {"name": target_lang, "code": target_lang[:2]})
    
    # Official TranslateGemma format (note: two blank lines before text)
    prompt = f"""<bos><start_of_turn>user
You are a professional {src['name']} ({src['code']}) to {tgt['name']} ({tgt['code']}) translator. Your goal is to accurately convey the meaning and nuances of the original {src['name']} text while adhering to {tgt['name']} grammar, vocabulary, and cultural sensitivities. Produce only the {tgt['name']} translation, without any additional explanations or commentary. Please translate the following {src['code']} text into {tgt['code']}:


{text}<end_of_turn>
<start_of_turn>model
"""
    
    output = model.create_completion(
        prompt,
        max_tokens=256,
        temperature=0.1,
        stop=["<end_of_turn>", "<start_of_turn>", "\n\n"]
    )
    return output["choices"][0]["text"].strip()


def translate_simple(model: Llama, text: str, source_lang: str, target_lang: str) -> str:
    """
    Simpler translation prompt (fallback if official format doesn't work).
    """
    prompt = f"Translate the following from {source_lang} to {target_lang}:\n\n{text}\n\nTranslation:"
    
    output = model.create_completion(
        prompt,
        max_tokens=256,
        temperature=0.3,
        stop=["\n\n", "Translate"]
    )
    return output["choices"][0]["text"].strip()


def medical_reasoning(model: Llama, symptoms: str) -> str:
    """Get medical triage from MedGemma."""
    prompt = f"""<bos><start_of_turn>user
You are a medical triage assistant. Based on the symptoms, provide:
1. Most likely condition
2. Severity (Low/Medium/High)  
3. Action (Self-care/Clinic/Urgent)

Symptoms: {symptoms}<end_of_turn>
<start_of_turn>model
"""
    
    output = model.create_completion(
        prompt,
        max_tokens=300,
        temperature=0.5,
        stop=["<end_of_turn>", "<start_of_turn>"]
    )
    return output["choices"][0]["text"].strip()


def run_test():
    """Run integration test with official TranslateGemma format."""
    print("=" * 60)
    print("NKU MODEL CHAIN TEST (V3 - Official TranslateGemma Format)")
    print("=" * 60)
    
    # Download & load models
    print("\n[PHASE 1] Loading Models\n")
    translategemma_path = download_model(TRANSLATEGEMMA_REPO, TRANSLATEGEMMA_FILE)
    medgemma_path = download_model(MEDGEMMA_REPO, MEDGEMMA_FILE)
    
    translategemma = load_model(translategemma_path)
    medgemma = load_model(medgemma_path)
    
    # Test cases
    print("\n[PHASE 2] Testing Translation\n")
    
    test_cases = [
        {"twi": "Me tirim yɛ me ya na me ho hyehye me", "expected": "headache and fever"},
        {"twi": "Me yafunu yɛ me ya", "expected": "stomach pain"},
    ]
    
    for i, test in enumerate(test_cases, 1):
        print(f"\n--- TEST {i} ---")
        print(f"Input (Twi): {test['twi']}")
        print(f"Expected keywords: {test['expected']}")
        
        # Try official format
        print("\n[A] Official TranslateGemma Format:")
        result_official = translate_official(translategemma, test['twi'], "twi", "english")
        print(f"    Result: '{result_official}'")
        
        # Try simple format
        print("\n[B] Simple Format (Fallback):")
        result_simple = translate_simple(translategemma, test['twi'], "Twi", "English")
        print(f"    Result: '{result_simple}'")
        
        # Use whichever worked
        english = result_official if result_official else result_simple
        
        if english:
            print("\n[C] MedGemma Triage:")
            triage = medical_reasoning(medgemma, english)
            print(f"    {triage[:200]}...")
    
    print("\n" + "=" * 60)
    print("TEST COMPLETE")
    print("=" * 60)


if __name__ == "__main__":
    try:
        run_test()
    except Exception as e:
        print(f"\n[ERROR] {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
