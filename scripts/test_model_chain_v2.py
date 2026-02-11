#!/usr/bin/env python3
"""
Nku Model Chain Integration Test - Enhanced Medical Prompts (V2)

Uses proper Gemma instruction format with:
  - <start_of_turn> / <end_of_turn> control tokens
  - Medical domain role prompting
  - Few-shot examples for clinical terminology
  - Lower temperature for consistency

Requirements:
  pip install llama-cpp-python huggingface_hub

Usage:
  python test_model_chain_v2.py
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


# --- Medical Terminology Glossary (Twi <-> English) ---
MEDICAL_GLOSSARY = {
    # Symptoms
    "tirim yɛ me ya": "I have a headache",
    "me ho hyehye me": "I feel hot / I have a fever",
    "me yare": "I am sick",
    "me ho yeraw me": "I feel pain",
    "me kɔn dɔ me": "I am thirsty",
    "me ani so yɛ me ya": "my eyes hurt",
    "me yafunu yɛ me ya": "I have stomach pain",
    # Conditions
    "atiridii": "malaria",
    "mogya ketewa": "anemia / low blood",
    "hyɛ": "fever",
    "pira": "wound",
    # Severity
    "ɛyɛ den": "it is severe",
    "kakra": "a little / mild",
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


def translate_to_english_v2(model: Llama, text: str, source_lang: str = "Twi") -> str:
    """
    Translate African language to English using Gemma instruction format.
    Uses few-shot medical examples for better clinical terminology handling.
    """
    # Build few-shot examples from glossary
    examples = """Examples of medical {lang} to English:
- "tirim yɛ me ya" → "I have a headache"
- "me ho hyehye me" → "I have a fever"
- "atiridii" → "malaria"
- "mogya ketewa" → "anemia"
- "me yafunu yɛ me ya" → "I have stomach pain"
""".format(lang=source_lang)
    
    prompt = f"""<start_of_turn>user
You are a medical translator specializing in {source_lang} to English translation for healthcare settings in Ghana. Translate the following patient statement accurately, preserving medical meaning.

{examples}

Now translate this patient statement:
"{text}"
<end_of_turn>
<start_of_turn>model
English translation: """
    
    output = model.create_completion(
        prompt,
        max_tokens=256,
        temperature=0.1,  # Lower for consistency
        stop=["<end_of_turn>", "\n\n", "<start_of_turn>"]
    )
    return output["choices"][0]["text"].strip().strip('"')


def translate_from_english_v2(model: Llama, text: str, target_lang: str = "Twi") -> str:
    """
    Translate English to African language using Gemma instruction format.
    Uses role prompting for medical context.
    """
    prompt = f"""<start_of_turn>user
You are a medical translator specializing in English to {target_lang} translation for community health workers in Ghana. Translate the following medical guidance into simple, clear {target_lang} that patients can understand.

English medical text:
"{text}"
<end_of_turn>
<start_of_turn>model
{target_lang} translation: """
    
    output = model.create_completion(
        prompt,
        max_tokens=512,
        temperature=0.2,
        stop=["<end_of_turn>", "<start_of_turn>"]
    )
    return output["choices"][0]["text"].strip().strip('"')


def medical_reasoning_v2(model: Llama, symptoms: str) -> str:
    """
    Get medical triage guidance from MedGemma using structured prompt.
    """
    prompt = f"""<start_of_turn>user
You are a medical triage assistant for community health workers in rural Ghana. Based on the patient's symptoms, provide:
1. Most likely conditions to consider
2. Severity assessment (Low/Medium/High)
3. Recommended action (Self-care / Visit clinic / Urgent referral)

Patient symptoms: {symptoms}
<end_of_turn>
<start_of_turn>model
**Triage Assessment:**
"""
    
    output = model.create_completion(
        prompt,
        max_tokens=512,
        temperature=0.5,
        stop=["<end_of_turn>", "<start_of_turn>", "Patient:"]
    )
    return output["choices"][0]["text"].strip()


def run_integration_test_v2():
    """Run the enhanced Nku model chain test with improved prompts."""
    print("=" * 60)
    print("NKU MODEL CHAIN INTEGRATION TEST (V2 - Enhanced Prompts)")
    print("=" * 60)
    
    # --- Step 1: Download models ---
    print("\n[PHASE 1] Downloading Models\n")
    
    translategemma_path = download_model(TRANSLATEGEMMA_REPO, TRANSLATEGEMMA_FILE)
    medgemma_path = download_model(MEDGEMMA_REPO, MEDGEMMA_FILE)
    
    # --- Step 2: Load models ---
    print("\n[PHASE 2] Loading Models\n")
    
    translategemma = load_model(translategemma_path)
    medgemma = load_model(medgemma_path)
    
    # --- Step 3: Test the chain ---
    print("\n[PHASE 3] Testing Model Chain (Enhanced Prompts)\n")
    
    # Test cases with expected translations
    test_cases = [
        {
            "twi": "Me tirim yɛ me ya na me ho hyehye me",
            "expected": "I have a headache and I have a fever"
        },
        {
            "twi": "Me yafunu yɛ me ya, ɛyɛ den",
            "expected": "I have severe stomach pain"
        },
    ]
    
    for i, test in enumerate(test_cases, 1):
        print(f"\n{'='*40}")
        print(f"TEST CASE {i}")
        print(f"{'='*40}")
        
        twi_input = test["twi"]
        expected = test["expected"]
        
        print(f"[INPUT] Twi: {twi_input}")
        print(f"[EXPECTED] English: {expected}")
        print("-" * 40)
        
        # Step 3a: Translate Twi → English (enhanced)
        print("[STEP 1] TranslateGemma: Twi → English (V2)")
        english_query = translate_to_english_v2(translategemma, twi_input, "Twi")
        print(f"         Result: {english_query}")
        
        # Step 3b: Medical reasoning in English (enhanced)
        print("\n[STEP 2] MedGemma: Medical Triage (V2)")
        medical_response = medical_reasoning_v2(medgemma, english_query)
        print(f"         Result:\n{medical_response[:400]}...")
        
        # Step 3c: Translate English → Twi (enhanced)
        print("\n[STEP 3] TranslateGemma: English → Twi (V2)")
        twi_response = translate_from_english_v2(translategemma, medical_response[:300], "Twi")
        print(f"         Result: {twi_response[:200]}...")
    
    # --- Summary ---
    print("\n" + "=" * 60)
    print("TEST COMPLETE (V2 - Enhanced Medical Prompts)")
    print("=" * 60)
    print(f"\n✓ Gemma instruction format: <start_of_turn> tokens")
    print(f"✓ Medical role prompting: Specialized translator persona")
    print(f"✓ Few-shot examples: Clinical terminology guidance")
    print(f"✓ Lower temperature: 0.1-0.2 for translation consistency")
    
    return True


if __name__ == "__main__":
    try:
        run_integration_test_v2()
    except Exception as e:
        print(f"\n[ERROR] Test failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
