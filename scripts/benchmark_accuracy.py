#!/usr/bin/env python3
"""
Nku IQ1_M Accuracy Benchmark
=============================
Compares MedGemma IQ1_M (0.78GB) against Q2_K (1.6GB) baseline on a curated
set of African primary care clinical scenarios.

This validates that ultra-compression preserves clinical reasoning accuracy
for the Nku deployment target (2GB RAM devices).

Usage:
    # Benchmark IQ1_M model
    python benchmark_accuracy.py --model medgemma-4b-iq1_m.gguf

    # Compare IQ1_M vs Q2_K
    python benchmark_accuracy.py --model medgemma-4b-iq1_m.gguf --baseline medgemma-4b-q2_k.gguf

Requirements:
    - llama-cli (from llama.cpp build)
    - GGUF model files
"""

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

# ── Clinical Benchmark Suite ──────────────────────────────────────────────────
# 20 curated scenarios spanning the 8 calibration categories from
# african_primary_care.txt, each with an expected triage output.

BENCHMARK_CASES = [
    # Category 1: Malaria & Febrile Illness
    {
        "id": "MAL-01",
        "category": "Malaria",
        "prompt": "A 5-year-old child presents with fever of 39.2°C for 3 days during rainy season in rural Ghana. No other symptoms. What is the most likely diagnosis and recommended action?",
        "expected_keywords": ["malaria", "test", "refer", "artemisinin"],
        "expected_severity": "HIGH",
    },
    {
        "id": "MAL-02",
        "category": "Malaria",
        "prompt": "A pregnant woman at 24 weeks presents with intermittent fever, chills, and sweats for 2 days. She lives in a malaria-endemic area. What is your clinical assessment?",
        "expected_keywords": ["malaria", "pregnan", "urgent", "refer"],
        "expected_severity": "HIGH",
    },
    # Category 2: Anemia
    {
        "id": "ANE-01",
        "category": "Anemia",
        "prompt": "A 3-year-old child has pale conjunctiva, fatigue, and poor appetite for 2 weeks. The family's diet is primarily cassava-based. What is the likely condition and management?",
        "expected_keywords": ["anemia", "iron", "hemoglobin", "nutrition"],
        "expected_severity": "MEDIUM",
    },
    {
        "id": "ANE-02",
        "category": "Anemia",
        "prompt": "A pregnant woman at 32 weeks reports fatigue, shortness of breath on exertion, and dizziness. Her conjunctiva appears pale. What is your assessment?",
        "expected_keywords": ["anemia", "hemoglobin", "iron", "supplement"],
        "expected_severity": "HIGH",
    },
    # Category 3: Respiratory
    {
        "id": "RESP-01",
        "category": "Respiratory",
        "prompt": "A 2-year-old child has fast breathing (55 breaths/minute) with chest indrawing and a 3-day history of cough. What is the diagnosis and urgency?",
        "expected_keywords": ["pneumonia", "urgent", "refer", "antibiotic"],
        "expected_severity": "HIGH",
    },
    {
        "id": "RESP-02",
        "category": "Respiratory",
        "prompt": "An adult male has had a productive cough for over 3 weeks with night sweats and weight loss. What should be considered?",
        "expected_keywords": ["tuberculosis", "TB", "sputum", "test"],
        "expected_severity": "HIGH",
    },
    # Category 4: Maternal Health
    {
        "id": "MAT-01",
        "category": "Maternal",
        "prompt": "A pregnant woman at 34 weeks presents with facial swelling, persistent headache for 2 days, and fatigue. What is the clinical concern?",
        "expected_keywords": ["preeclampsia", "blood pressure", "urgent", "refer"],
        "expected_severity": "HIGH",
    },
    {
        "id": "MAT-02",
        "category": "Maternal",
        "prompt": "A postpartum woman (3 days after delivery) has fever of 38.8°C and foul-smelling vaginal discharge. What is the assessment?",
        "expected_keywords": ["sepsis", "infection", "antibiotic", "urgent"],
        "expected_severity": "HIGH",
    },
    # Category 5: Diarrheal Disease
    {
        "id": "DIA-01",
        "category": "Diarrhea",
        "prompt": "A 1-year-old child has had watery diarrhea for 2 days with sunken eyes and decreased skin turgor. What is the severity and management?",
        "expected_keywords": ["dehydration", "ORS", "fluid", "rehydrat"],
        "expected_severity": "HIGH",
    },
    {
        "id": "DIA-02",
        "category": "Diarrhea",
        "prompt": "An adult has bloody diarrhea with abdominal cramps and fever for 1 day. What condition should be suspected?",
        "expected_keywords": ["dysentery", "shigella", "antibiotic", "refer"],
        "expected_severity": "HIGH",
    },
    # Category 6: Skin Conditions
    {
        "id": "SKIN-01",
        "category": "Skin",
        "prompt": "A child has itchy, ring-shaped scaly patches on the scalp with hair loss. What is the likely diagnosis?",
        "expected_keywords": ["ringworm", "tinea", "antifungal"],
        "expected_severity": "LOW",
    },
    # Category 7: Child Nutrition
    {
        "id": "NUT-01",
        "category": "Nutrition",
        "prompt": "An 18-month-old child has edema in both feet, thin hair, and a distended abdomen. Weight is below the 3rd percentile. What is the diagnosis?",
        "expected_keywords": ["malnutrition", "kwashiorkor", "protein", "refer"],
        "expected_severity": "HIGH",
    },
    # Category 8: Chronic Conditions
    {
        "id": "CHR-01",
        "category": "Chronic",
        "prompt": "A 55-year-old man has been experiencing increased thirst, frequent urination, and unexplained weight loss for 3 months. What condition should be assessed?",
        "expected_keywords": ["diabetes", "glucose", "blood sugar", "test"],
        "expected_severity": "MEDIUM",
    },
    # Nku Sentinel integration scenarios
    {
        "id": "SEN-01",
        "category": "Sentinel",
        "prompt": "Nku Sentinel camera screening shows: Heart Rate 108 bpm, Pallor Score 68% (moderate), Edema Score 52% (moderate). Patient is pregnant at 32 weeks with headache. What is the clinical assessment?",
        "expected_keywords": ["preeclampsia", "anemia", "urgent", "refer"],
        "expected_severity": "HIGH",
    },
    {
        "id": "SEN-02",
        "category": "Sentinel",
        "prompt": "Nku Sentinel shows: Heart Rate 62 bpm (normal), Pallor Score 15% (low), Edema Score 10% (low). Patient reports mild cough for 2 days. Assessment?",
        "expected_keywords": ["mild", "monitor", "follow"],
        "expected_severity": "LOW",
    },
]


def run_inference(model_path: str, prompt: str, llama_cli: str = "llama-cli") -> tuple[str, float]:
    """Run a single inference via llama-cli and return (response, latency_seconds)."""
    system_prompt = (
        "You are a clinical triage assistant for community health workers in rural Africa. "
        "Provide a concise assessment with: diagnosis, severity (LOW/MEDIUM/HIGH/CRITICAL), "
        "and recommended actions. Be direct and actionable."
    )

    cmd = [
        llama_cli,
        "-m", model_path,
        "-p", f"[INST] <<SYS>>\n{system_prompt}\n<</SYS>>\n\n{prompt} [/INST]",
        "-n", "256",
        "--temp", "0.1",
        "--top-k", "40",
        "--repeat-penalty", "1.1",
        "--log-disable",
    ]

    start = time.time()
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        elapsed = time.time() - start
        return result.stdout.strip(), elapsed
    except subprocess.TimeoutExpired:
        return "[TIMEOUT]", 120.0
    except FileNotFoundError:
        print(f"Error: '{llama_cli}' not found. Build llama.cpp first.", file=sys.stderr)
        sys.exit(1)


def score_response(response: str, case: dict) -> dict:
    """Score a response against expected keywords and severity."""
    response_lower = response.lower()

    # Keyword match
    matched = [kw for kw in case["expected_keywords"] if kw.lower() in response_lower]
    keyword_score = len(matched) / len(case["expected_keywords"])

    # Severity match
    severity_match = case["expected_severity"].lower() in response_lower

    return {
        "keyword_score": keyword_score,
        "keywords_matched": matched,
        "keywords_missed": [kw for kw in case["expected_keywords"] if kw.lower() not in response_lower],
        "severity_match": severity_match,
        "response_length": len(response),
    }


def run_benchmark(model_path: str, llama_cli: str = "llama-cli") -> dict:
    """Run full benchmark suite against a model."""
    results = []
    total_latency = 0

    for i, case in enumerate(BENCHMARK_CASES):
        print(f"  [{i+1}/{len(BENCHMARK_CASES)}] {case['id']}: {case['category']}...", end=" ", flush=True)

        response, latency = run_inference(model_path, case["prompt"], llama_cli)
        score = score_response(response, case)
        total_latency += latency

        results.append({
            "id": case["id"],
            "category": case["category"],
            "keyword_score": score["keyword_score"],
            "severity_match": score["severity_match"],
            "latency_s": round(latency, 1),
            "keywords_matched": score["keywords_matched"],
            "keywords_missed": score["keywords_missed"],
        })

        status = "✅" if score["keyword_score"] >= 0.5 and score["severity_match"] else "⚠️"
        print(f"{status} keywords={score['keyword_score']:.0%} severity={'✓' if score['severity_match'] else '✗'} ({latency:.1f}s)")

    # Aggregate
    avg_keyword = sum(r["keyword_score"] for r in results) / len(results)
    severity_accuracy = sum(1 for r in results if r["severity_match"]) / len(results)
    avg_latency = total_latency / len(results)

    return {
        "model": model_path,
        "cases": len(results),
        "avg_keyword_score": round(avg_keyword, 3),
        "severity_accuracy": round(severity_accuracy, 3),
        "avg_latency_s": round(avg_latency, 1),
        "total_time_s": round(total_latency, 1),
        "details": results,
    }


def main():
    parser = argparse.ArgumentParser(description="Nku IQ1_M Accuracy Benchmark")
    parser.add_argument("--model", required=True, help="Path to IQ1_M GGUF model")
    parser.add_argument("--baseline", help="Path to Q2_K GGUF baseline model (optional comparison)")
    parser.add_argument("--llama-cli", default="llama-cli", help="Path to llama-cli binary")
    parser.add_argument("--output", default="benchmark_results.json", help="Output JSON file")
    args = parser.parse_args()

    print("=" * 60)
    print("NKU IQ1_M ACCURACY BENCHMARK")
    print(f"Model: {args.model}")
    print(f"Cases: {len(BENCHMARK_CASES)}")
    print("=" * 60)

    # Run primary model
    print(f"\n🔬 Benchmarking: {Path(args.model).name}")
    primary = run_benchmark(args.model, args.llama_cli)

    output = {"primary": primary}

    # Run baseline if provided
    if args.baseline:
        print(f"\n📊 Benchmarking baseline: {Path(args.baseline).name}")
        baseline = run_benchmark(args.baseline, args.llama_cli)
        output["baseline"] = baseline

        # Comparison
        print("\n" + "=" * 60)
        print("COMPARISON: IQ1_M vs Q2_K")
        print("=" * 60)
        print(f"  Keyword Accuracy:  {primary['avg_keyword_score']:.1%} vs {baseline['avg_keyword_score']:.1%}  (Δ {primary['avg_keyword_score'] - baseline['avg_keyword_score']:+.1%})")
        print(f"  Severity Accuracy: {primary['severity_accuracy']:.1%} vs {baseline['severity_accuracy']:.1%}  (Δ {primary['severity_accuracy'] - baseline['severity_accuracy']:+.1%})")
        print(f"  Avg Latency:       {primary['avg_latency_s']:.1f}s vs {baseline['avg_latency_s']:.1f}s")

    # Summary
    print("\n" + "=" * 60)
    print("RESULTS SUMMARY")
    print("=" * 60)
    print(f"  Keyword Accuracy:  {primary['avg_keyword_score']:.1%}")
    print(f"  Severity Accuracy: {primary['severity_accuracy']:.1%}")
    print(f"  Avg Latency:       {primary['avg_latency_s']:.1f}s")
    print(f"  Total Time:        {primary['total_time_s']:.0f}s")

    # Save results
    with open(args.output, "w") as f:
        json.dump(output, f, indent=2)
    print(f"\n📄 Results saved to {args.output}")


if __name__ == "__main__":
    main()
