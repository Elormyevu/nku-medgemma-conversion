#!/usr/bin/env python3
"""
MedGemma IQ1_M Benchmark — Full MedQA via llama-server HTTP API

Runs ALL 1,273 MedQA-USMLE questions through the IQ1_M model.
Reports:
  1. Overall accuracy → compare to published baseline (64.4% v1, 69% v1.5)
  2. Primary care subset accuracy → domain-specific retention

Uses forced-letter prompt format for reliable parsing (0% parse failures).
Requires: llama-server running on localhost:8787.
"""

import json
import re
import random
import sys
import time
import urllib.request
from pathlib import Path

LLAMA_URL = "http://localhost:8787/completion"
RESULTS_DIR = Path("benchmark")
RESULTS_DIR.mkdir(exist_ok=True)

random.seed(42)

# Primary care / CHW-relevant domain keywords
PRIMARY_CARE_KEYWORDS = [
    'malaria', 'cholera', 'typhoid', 'tuberculosis', 'tb ', 'pneumonia',
    'meningitis', 'diarrhea', 'diarrhoea', 'dysentery', 'dehydrat',
    'oral rehydration', 'HIV', 'measles', 'tetanus', 'hookworm',
    'schistosom', 'parasit', 'helminth',
    'pregnan', 'preeclampsia', 'eclampsia', 'postpartum', 'obstetric',
    'gestation', 'labor and delivery', 'neonatal', 'newborn',
    'breastfeed',
    'infant', '2-year-old', '3-year-old', '4-year-old', '5-year-old',
    'malnutrition', 'kwashiorkor', 'marasmus', 'vitamin a deficiency',
    'iron deficiency anemia', 'sickle cell',
    'snakebite', 'snake bite', 'seizure', 'convulsion', 'unconscious',
    'acute abdomen', 'hemorrhag', 'haemorrhag', 'shock',
]


def load_medqa():
    cache = RESULTS_DIR / "medqa_test_cached.json"
    with open(cache) as f:
        raw = json.load(f)
    for q in raw:
        q["is_primary_care"] = any(
            kw.lower() in q["question"].lower() for kw in PRIMARY_CARE_KEYWORDS
        )
    return raw


def query_llama(prompt: str) -> str:
    payload = json.dumps({
        "prompt": prompt,
        "n_predict": 10,
        "temperature": 0.1,
        "top_k": 1,
        "stop": ["<end_of_turn>", "\n"],
    }).encode()
    req = urllib.request.Request(
        LLAMA_URL, data=payload,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return json.loads(resp.read()).get("content", "").strip()
    except Exception as e:
        return f"ERROR: {e}"


def extract_answer(response: str) -> str:
    """Extract letter from forced-letter output like 'B) Preeclampsia'."""
    resp = response.strip()
    if not resp:
        return "?"
    # Pattern: starts with letter
    m = re.match(r'^([A-Da-d])\b', resp)
    if m:
        return m.group(1).upper()
    # Fallback: any standalone letter
    m = re.search(r'\b([A-D])\b', resp)
    if m:
        return m.group(1).upper()
    return "?"


def main():
    print("=" * 70)
    print("MedGemma IQ1_M — Full MedQA Benchmark")
    print("Baseline: MedGemma 4B = 64.4% (v1) / 69% (v1.5)")
    print("=" * 70)

    try:
        urllib.request.urlopen("http://localhost:8787/health", timeout=5)
    except Exception:
        print("ERROR: llama-server not running on localhost:8787")
        sys.exit(1)

    print("Loading MedQA dataset...")
    all_q = load_medqa()
    pc_ids = {q["id"] for q in all_q if q["is_primary_care"]}
    print(f"Total: {len(all_q)}, Primary care: {len(pc_ids)}")

    results = []
    start = time.time()
    correct = 0
    pc_correct = 0
    pc_total = 0
    unparsed = 0

    for i, q in enumerate(all_q):
        opts = "\n".join(f"{k}) {v}" for k, v in q["options"].items())

        # Forced-letter prompt: model completes "The correct answer is ("
        prompt = (
            f"<start_of_turn>user\n{q['question']}\n\n{opts}"
            f"<end_of_turn>\n<start_of_turn>model\n"
            f"The correct answer is ("
        )

        t0 = time.time()
        response = query_llama(prompt)
        elapsed = time.time() - t0

        predicted = extract_answer(response)
        is_correct = predicted == q["correct_option"]
        if is_correct:
            correct += 1

        is_pc = q["id"] in pc_ids
        if is_pc:
            pc_total += 1
            if is_correct:
                pc_correct += 1

        if predicted == "?":
            unparsed += 1

        results.append({
            "id": q["id"],
            "expected": q["correct_option"],
            "predicted": predicted,
            "correct": is_correct,
            "is_primary_care": is_pc,
            "response": response[:150],
            "time_s": round(elapsed, 1),
        })

        if (i + 1) % 50 == 0 or i == 0:
            elapsed_total = time.time() - start
            acc = 100 * correct / (i + 1)
            eta = (elapsed_total / (i + 1)) * (len(all_q) - i - 1)
            pc_acc = f"{100*pc_correct/pc_total:.1f}%" if pc_total > 0 else "N/A"
            print(
                f"  [{i+1}/{len(all_q)}] "
                f"Overall: {correct}/{i+1} ({acc:.1f}%) | "
                f"PC: {pc_correct}/{pc_total} ({pc_acc}) | "
                f"Unparsed: {unparsed} | "
                f"ETA: {eta/60:.0f}m"
            )

    total_time = time.time() - start
    overall_acc = 100 * correct / len(all_q)
    pc_acc = 100 * pc_correct / pc_total if pc_total > 0 else 0

    print("\n" + "=" * 70)
    print("FINAL RESULTS")
    print("=" * 70)
    print(f"{'Metric':<42} {'Score':>8} {'Base':>8} {'Δ':>8}")
    print("-" * 70)
    print(f"{'Overall MedQA (' + str(len(all_q)) + ' questions)':<42} {overall_acc:>7.1f}% {69.0:>7.1f}% {overall_acc-69:>+7.1f}pp")
    print(f"{'Primary Care (' + str(pc_total) + ' questions)':<42} {pc_acc:>7.1f}%   {'N/A':>6}")
    print(f"{'Unparsed responses':<42} {unparsed:>7}")
    print(f"{'Random chance (4 options)':<42} {'25.0':>7}%")
    print(f"{'Total time':<42} {total_time/60:>7.1f}m")
    print(f"{'Avg per question':<42} {total_time/len(all_q):>7.1f}s")

    output = {
        "model": "medgemma-4b-iq1_m.gguf",
        "quantization": "IQ1_M (1.75 bits/weight)",
        "size_gb": 1.1,
        "baselines": {"medgemma_4b_v1": 64.4, "medgemma_4b_v1_5": 69.0},
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "total_time_s": round(total_time),
        "overall": {
            "n": len(all_q), "correct": correct,
            "accuracy_pct": round(overall_acc, 1),
            "degradation_pp": round(69 - overall_acc, 1),
        },
        "primary_care": {
            "n": pc_total, "correct": pc_correct,
            "accuracy_pct": round(pc_acc, 1),
        },
        "unparsed": unparsed,
        "random_chance_pct": 25.0,
        "results": results,
    }

    out_path = RESULTS_DIR / "medqa_benchmark_results.json"
    with open(out_path, "w") as f:
        json.dump(output, f, indent=2)
    print(f"\nResults saved to {out_path}")


if __name__ == "__main__":
    main()
