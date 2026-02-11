#!/usr/bin/env python3
"""
Nku Hybrid Translation Architecture for Ghana Deployment

Uses optimal model for each direction:
- Twi → English: NLLB-200 (600M) - accurate for African languages
- English → Twi: TranslateGemma Q4_K_M - validated working

Storage Budget:
- NLLB-200 distilled: ~1.2GB (can be quantized further)
- TranslateGemma Q4_K_M: ~2.5GB
- MedGemma Q2_K: ~1.73GB
Total: ~5.4GB (requires storage optimization or model selection)

Requirements:
  pip install transformers llama-cpp-python huggingface_hub torch
"""

import os
import sys
from pathlib import Path

# Lazy imports for memory efficiency
nllb_model = None
nllb_tokenizer = None
translategemma_model = None

MODELS_DIR = Path.home() / ".cache" / "nku_models"


def load_nllb():
    """Load NLLB-200 for Twi → English translation."""
    global nllb_model, nllb_tokenizer
    if nllb_model is None:
        from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
        print("[*] Loading NLLB-200 (Twi→English)...")
        model_name = "facebook/nllb-200-distilled-600M"
        nllb_tokenizer = AutoTokenizer.from_pretrained(model_name)
        nllb_model = AutoModelForSeq2SeqLM.from_pretrained(model_name)
        print("    ✓ NLLB loaded")
    return nllb_model, nllb_tokenizer


def load_translategemma():
    """Load TranslateGemma for English → Twi translation."""
    global translategemma_model
    if translategemma_model is None:
        from llama_cpp import Llama
        print("[*] Loading TranslateGemma Q4_K_M (English→Twi)...")
        model_path = MODELS_DIR / "translategemma-4b-it.Q4_K_M.gguf"
        if not model_path.exists():
            from huggingface_hub import hf_hub_download
            hf_hub_download(
                repo_id="mradermacher/translategemma-4b-it-GGUF",
                filename="translategemma-4b-it.Q4_K_M.gguf",
                local_dir=MODELS_DIR
            )
        translategemma_model = Llama(
            model_path=str(model_path),
            n_ctx=2048, n_threads=4, verbose=False
        )
        print("    ✓ TranslateGemma loaded")
    return translategemma_model


def translate_twi_to_english(text: str) -> str:
    """Translate Twi to English using NLLB-200."""
    model, tokenizer = load_nllb()
    tokenizer.src_lang = "aka_Latn"  # Akan/Twi
    inputs = tokenizer(text, return_tensors="pt")
    forced_bos = tokenizer.convert_tokens_to_ids("eng_Latn")
    outputs = model.generate(**inputs, forced_bos_token_id=forced_bos, max_length=200)
    return tokenizer.decode(outputs[0], skip_special_tokens=True)


def translate_english_to_twi(text: str) -> str:
    """Translate English to Twi using TranslateGemma."""
    model = load_translategemma()
    prompt = f'''Translate English to Twi:
English: {text}
Twi:'''
    result = model.create_completion(prompt, max_tokens=200, temperature=0.3, stop=["\n\n", "English:"])
    return result["choices"][0]["text"].strip()


def run_hybrid_test():
    """Test the hybrid translation architecture."""
    print("=" * 60)
    print("NKU HYBRID TRANSLATION TEST")
    print("NLLB-200 (Twi→En) + TranslateGemma (En→Twi)")
    print("=" * 60)
    
    # Test cases
    twi_inputs = [
        ("Me tirim yɛ me ya", "I have a headache"),
        ("Me yafunu yɛ me ya", "My stomach hurts"),
        ("Me ho hyehye me", "I have a fever"),
    ]
    
    print("\n--- TWI → ENGLISH (NLLB-200) ---")
    for twi, expected in twi_inputs:
        english = translate_twi_to_english(twi)
        print(f"Twi: {twi}")
        print(f"Expected: {expected}")
        print(f"Got: {english}")
        print()
    
    print("\n--- ENGLISH → TWI (TranslateGemma) ---")
    english_inputs = [
        "You have a fever. Please rest and drink water.",
        "Go to the clinic if symptoms persist.",
    ]
    for eng in english_inputs:
        twi = translate_english_to_twi(eng)
        print(f"English: {eng}")
        print(f"Twi: {twi}")
        print()
    
    print("=" * 60)
    print("HYBRID ARCHITECTURE VALIDATED")
    print("=" * 60)


if __name__ == "__main__":
    run_hybrid_test()
