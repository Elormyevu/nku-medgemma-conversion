#!/usr/bin/env python3
"""
Nku MedGemma CHW Triage Benchmark

Part A: 20 CHW triage vignettes with sensor-augmented prompts
Part B: Same vignettes, text-only (no sensor data) — measures sensor impact

Supports running against multiple quantized models to compare triage accuracy.

Usage:
    # Run against a specific model (llama-server on given port)
    python benchmark/nku_medgemma_benchmark.py --model medgemma-4b-q4_k_m --port 8787

    # Run against a different quant
    python benchmark/nku_medgemma_benchmark.py --model medgemma-4b-iq2_xs --port 8787

    # Compare all saved results
    python benchmark/nku_medgemma_benchmark.py --compare
"""

import argparse
import json
import re
import sys
import time
import urllib.request
from pathlib import Path
from typing import Optional

BENCHMARK_DIR = Path(__file__).parent
RESULTS_DIR = BENCHMARK_DIR / "chw_triage_results"


# ═══════════════════════════════════════════════════════════════════════════════
# CHW Triage Vignettes — 20 clinically realistic Sub-Saharan Africa scenarios
# ═══════════════════════════════════════════════════════════════════════════════

CHW_VIGNETTES = [
    {
        "id": "V01", "name": "Severe malaria with anemia",
        "symptoms": "High fever for 3 days, chills, body aches, very weak, not eating",
        "sensors": {"hr_bpm": 128, "hr_conf": 0.82, "pallor_score": 0.75, "pallor_sev": "SEVERE",
                    "jaundice_score": 0.3, "jaundice_sev": "MILD"},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "malaria",
    },
    {
        "id": "V02", "name": "Uncomplicated URTI",
        "symptoms": "Runny nose, mild cough for 2 days, no fever",
        "sensors": {"hr_bpm": 78, "hr_conf": 0.85},
        "expected_severity": "LOW", "expected_urgency": "ROUTINE",
        "expected_category": "GREEN", "expected_concern": "upper respiratory",
    },
    {
        "id": "V03", "name": "Preeclampsia warning",
        "symptoms": "Headache, swollen face and hands, blurry vision, 32 weeks pregnant",
        "sensors": {"hr_bpm": 98, "hr_conf": 0.79, "edema_score": 0.8, "edema_sev": "SIGNIFICANT",
                    "is_pregnant": True},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "preeclampsia",
    },
    {
        "id": "V04", "name": "Childhood pneumonia",
        "symptoms": "4-year-old, fast breathing, chest indrawing, fever 39°C for 2 days",
        "sensors": {"hr_bpm": 140, "hr_conf": 0.75, "respiratory_risk": "HIGH",
                    "respiratory_conf": 0.7},
        "expected_severity": "HIGH", "expected_urgency": "URGENT",
        "expected_category": "ORANGE", "expected_concern": "pneumonia",
    },
    {
        "id": "V05", "name": "Moderate anemia",
        "symptoms": "Tired all the time, dizzy when standing, pale inside eyelids",
        "sensors": {"hr_bpm": 95, "hr_conf": 0.80, "pallor_score": 0.55, "pallor_sev": "MODERATE"},
        "expected_severity": "MEDIUM", "expected_urgency": "SOON",
        "expected_category": "YELLOW", "expected_concern": "anemia",
    },
    {
        "id": "V06", "name": "Neonatal jaundice",
        "symptoms": "3-day-old baby, yellow skin and eyes, not feeding well",
        "sensors": {"jaundice_score": 0.82, "jaundice_sev": "SEVERE"},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "jaundice",
    },
    {
        "id": "V07", "name": "Dehydration from diarrhea",
        "symptoms": "Watery diarrhea 8 times today, vomiting, sunken eyes, very thirsty",
        "sensors": {"hr_bpm": 115, "hr_conf": 0.78},
        "expected_severity": "HIGH", "expected_urgency": "URGENT",
        "expected_category": "ORANGE", "expected_concern": "dehydration",
    },
    {
        "id": "V08", "name": "Normal prenatal check",
        "symptoms": "28 weeks pregnant, feeling well, slight ankle swelling",
        "sensors": {"hr_bpm": 82, "hr_conf": 0.88, "edema_score": 0.2, "edema_sev": "NORMAL",
                    "is_pregnant": True},
        "expected_severity": "LOW", "expected_urgency": "ROUTINE",
        "expected_category": "GREEN", "expected_concern": "normal",
    },
    {
        "id": "V09", "name": "Tuberculosis suspect",
        "symptoms": "Cough for 3 weeks, night sweats, weight loss, coughing blood",
        "sensors": {"hr_bpm": 92, "hr_conf": 0.81, "respiratory_risk": "MODERATE",
                    "respiratory_conf": 0.65},
        "expected_severity": "HIGH", "expected_urgency": "URGENT",
        "expected_category": "ORANGE", "expected_concern": "tuberculosis",
    },
    {
        "id": "V10", "name": "Sickle cell crisis",
        "symptoms": "Known sickle cell, severe bone pain, fever, very pale",
        "sensors": {"hr_bpm": 135, "hr_conf": 0.76, "pallor_score": 0.85, "pallor_sev": "SEVERE",
                    "jaundice_score": 0.45, "jaundice_sev": "MILD"},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "sickle cell",
    },
    {
        "id": "V11", "name": "Mild gastroenteritis",
        "symptoms": "Stomach cramps, loose stools 3 times, no blood, drinking fluids",
        "sensors": {"hr_bpm": 80, "hr_conf": 0.84},
        "expected_severity": "LOW", "expected_urgency": "ROUTINE",
        "expected_category": "GREEN", "expected_concern": "gastroenteritis",
    },
    {
        "id": "V12", "name": "Postpartum hemorrhage",
        "symptoms": "Delivered 2 hours ago, heavy bleeding not stopping, dizzy, cold sweaty",
        "sensors": {"hr_bpm": 145, "hr_conf": 0.72, "pallor_score": 0.9, "pallor_sev": "SEVERE",
                    "is_pregnant": True},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "hemorrhage",
    },
    {
        "id": "V13", "name": "Mild asthma exacerbation",
        "symptoms": "Wheezing, tight chest, can speak full sentences, using inhaler",
        "sensors": {"hr_bpm": 90, "hr_conf": 0.83, "respiratory_risk": "LOW",
                    "respiratory_conf": 0.72},
        "expected_severity": "LOW", "expected_urgency": "ROUTINE",
        "expected_category": "GREEN", "expected_concern": "asthma",
    },
    {
        "id": "V14", "name": "Hepatitis A",
        "symptoms": "Yellow eyes, dark urine, stomach pain right side, tired, no appetite",
        "sensors": {"jaundice_score": 0.65, "jaundice_sev": "MODERATE"},
        "expected_severity": "MEDIUM", "expected_urgency": "SOON",
        "expected_category": "YELLOW", "expected_concern": "hepatitis",
    },
    {
        "id": "V15", "name": "Febrile seizure in child",
        "symptoms": "2-year-old, fever 40°C, had shaking episode lasting 2 minutes, now drowsy",
        "sensors": {"hr_bpm": 155, "hr_conf": 0.70},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "seizure",
    },
    {
        "id": "V16", "name": "Skin infection",
        "symptoms": "Red swollen area on leg, warm to touch, small amount of pus",
        "sensors": {"hr_bpm": 85, "hr_conf": 0.86},
        "expected_severity": "LOW", "expected_urgency": "ROUTINE",
        "expected_category": "GREEN", "expected_concern": "infection",
    },
    {
        "id": "V17", "name": "Eclampsia",
        "symptoms": "38 weeks pregnant, seizure, unconscious, very swollen face",
        "sensors": {"hr_bpm": 130, "hr_conf": 0.68, "edema_score": 0.92, "edema_sev": "SIGNIFICANT",
                    "is_pregnant": True},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "eclampsia",
    },
    {
        "id": "V18", "name": "Chronic cough, low risk",
        "symptoms": "Dry cough for 1 week, no fever, no night sweats, eating well",
        "sensors": {"hr_bpm": 74, "hr_conf": 0.87, "respiratory_risk": "LOW",
                    "respiratory_conf": 0.80},
        "expected_severity": "LOW", "expected_urgency": "ROUTINE",
        "expected_category": "GREEN", "expected_concern": "cough",
    },
    {
        "id": "V19", "name": "Snake bite",
        "symptoms": "Bitten on foot 1 hour ago, swelling spreading up leg, very painful",
        "sensors": {"hr_bpm": 110, "hr_conf": 0.77},
        "expected_severity": "HIGH", "expected_urgency": "IMMEDIATE",
        "expected_category": "RED", "expected_concern": "snake",
    },
    {
        "id": "V20", "name": "Iron deficiency pregnancy",
        "symptoms": "24 weeks pregnant, tired, craving ice, slightly pale",
        "sensors": {"hr_bpm": 88, "hr_conf": 0.82, "pallor_score": 0.4, "pallor_sev": "MILD",
                    "is_pregnant": True},
        "expected_severity": "MEDIUM", "expected_urgency": "SOON",
        "expected_category": "YELLOW", "expected_concern": "anemia",
    },
]


# ═══════════════════════════════════════════════════════════════════════════════
# Prompt Building (mirrors ClinicalReasoner.kt)
# ═══════════════════════════════════════════════════════════════════════════════

def _build_nku_prompt(vignette: dict, include_sensors: bool = True) -> str:
    """Build a prompt matching ClinicalReasoner.kt's structured format."""
    v = vignette
    prompt = (
        "<start_of_turn>user\n"
        "You are a clinical triage assistant for Community Health Workers in Sub-Saharan Africa.\n"
        "Analyze the patient data below and provide a structured assessment.\n\n"
    )

    if include_sensors and v.get("sensors"):
        s = v["sensors"]
        prompt += "=== SENSOR BIOMARKERS ===\n"
        if "hr_bpm" in s:
            prompt += f"Heart Rate: {s['hr_bpm']} BPM (confidence: {s.get('hr_conf', 0.5):.2f})\n"
            prompt += "  Method: Remote photoplethysmography — green channel from facial video\n"
        if "pallor_score" in s:
            prompt += f"Pallor Score: {s['pallor_score']:.2f} (severity: {s['pallor_sev']})\n"
            prompt += "  Method: HSV saturation of conjunctival tissue\n"
        if "jaundice_score" in s:
            prompt += f"Jaundice Score: {s['jaundice_score']:.2f} (severity: {s['jaundice_sev']})\n"
            prompt += "  Method: Scleral yellow ratio with sigmoid mapping\n"
        if "edema_score" in s:
            prompt += f"Edema Score: {s['edema_score']:.2f} (severity: {s['edema_sev']})\n"
            prompt += "  Method: Eye Aspect Ratio from MediaPipe landmarks\n"
        if "respiratory_risk" in s:
            prompt += f"Respiratory Risk: {s['respiratory_risk']} (confidence: {s.get('respiratory_conf', 0.5):.2f})\n"
            prompt += "  Method: HeAR audio classifier (cough/wheeze/crackle detection)\n"
        if s.get("is_pregnant"):
            prompt += "Pregnancy Status: CONFIRMED\n"
        prompt += "\n"

    prompt += "=== REPORTED SYMPTOMS ===\n"
    prompt += f"- <<<{v['symptoms']}>>>\n\n"
    prompt += (
        "Provide your assessment in this EXACT format:\n"
        "SEVERITY: [LOW/MEDIUM/HIGH]\n"
        "URGENCY: [ROUTINE/SOON/URGENT/IMMEDIATE]\n"
        "TRIAGE_CATEGORY: [GREEN/YELLOW/ORANGE/RED]\n"
        "PRIMARY_CONCERN: [one-line diagnosis or concern]\n"
        "RECOMMENDATIONS:\n"
        "- [action 1]\n"
        "- [action 2]\n"
        "<end_of_turn>\n"
        "<start_of_turn>model\n"
    )
    return prompt


def _parse_triage_response(response: str) -> dict:
    """Parse structured triage output from MedGemma."""
    parsed = {
        "severity": None, "urgency": None, "category": None,
        "concern": None, "raw": response[:300],
    }
    sev = re.search(r'SEVERITY:\s*(LOW|MEDIUM|HIGH)', response, re.IGNORECASE)
    urg = re.search(r'URGENCY:\s*(ROUTINE|SOON|URGENT|IMMEDIATE)', response, re.IGNORECASE)
    cat = re.search(r'TRIAGE_CATEGORY:\s*(GREEN|YELLOW|ORANGE|RED)', response, re.IGNORECASE)
    con = re.search(r'PRIMARY_CONCERN:\s*(.+)', response, re.IGNORECASE)

    if sev: parsed["severity"] = sev.group(1).upper()
    if urg: parsed["urgency"] = urg.group(1).upper()
    if cat: parsed["category"] = cat.group(1).upper()
    if con: parsed["concern"] = con.group(1).strip()
    return parsed


# ═══════════════════════════════════════════════════════════════════════════════
# Inference
# ═══════════════════════════════════════════════════════════════════════════════

def query_llama(prompt: str, port: int = 8787, max_tokens: int = 300) -> Optional[str]:
    """Query llama-server for inference."""
    url = f"http://localhost:{port}/completion"
    payload = json.dumps({
        "prompt": prompt,
        "n_predict": max_tokens,
        "temperature": 0.3,
        "top_k": 40,
        "stop": ["<end_of_turn>", "\n\n\n"],
    }).encode()
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            return json.loads(resp.read()).get("content", "").strip()
    except Exception as e:
        return None


def check_server(port: int = 8787) -> bool:
    try:
        urllib.request.urlopen(f"http://localhost:{port}/health", timeout=3)
        return True
    except Exception:
        return False


# ═══════════════════════════════════════════════════════════════════════════════
# Part A: CHW Triage Vignettes (sensor-augmented)
# Part B: Text-only comparison
# ═══════════════════════════════════════════════════════════════════════════════

def run_triage(model_name: str, port: int, include_sensors: bool) -> list:
    """Run triage vignettes against a model."""
    mode = "Sensor-Augmented" if include_sensors else "Text-Only"
    part = "A" if include_sensors else "B"
    print(f"\n{'=' * 78}")
    print(f"PART {part}: {mode} CHW Triage — {model_name} (N={len(CHW_VIGNETTES)})")
    print(f"{'=' * 78}\n")

    results = []
    counts = {"severity": 0, "urgency": 0, "category": 0, "concern": 0}

    for v in CHW_VIGNETTES:
        prompt = _build_nku_prompt(v, include_sensors=include_sensors)
        t0 = time.time()
        response = query_llama(prompt, port=port)
        elapsed = time.time() - t0

        if response is None:
            print(f"  ⛔ {v['id']} {v['name']:<35} — SERVER ERROR")
            results.append({"id": v["id"], "error": True})
            continue

        parsed = _parse_triage_response(response)

        sev_ok = parsed["severity"] == v["expected_severity"]
        urg_ok = parsed["urgency"] == v["expected_urgency"]
        cat_ok = parsed["category"] == v["expected_category"]
        con_ok = (v["expected_concern"].lower() in (parsed["concern"] or "").lower()) if parsed["concern"] else False

        if sev_ok: counts["severity"] += 1
        if urg_ok: counts["urgency"] += 1
        if cat_ok: counts["category"] += 1
        if con_ok: counts["concern"] += 1

        status = "✅" if (sev_ok and cat_ok) else "⚠️" if (sev_ok or cat_ok) else "❌"
        print(f"  {status} {v['id']} {v['name']:<35} "
              f"Sev:{parsed['severity'] or '?':<6} Cat:{parsed['category'] or '?':<6} "
              f"({elapsed:.1f}s)")

        results.append({
            "id": v["id"], "name": v["name"],
            "expected": {"severity": v["expected_severity"], "urgency": v["expected_urgency"],
                         "category": v["expected_category"], "concern": v["expected_concern"]},
            "predicted": parsed, "time_s": round(elapsed, 1),
            "correct": {"severity": sev_ok, "urgency": urg_ok, "category": cat_ok, "concern": con_ok},
        })

    n = len([r for r in results if not r.get("error")])
    if n > 0:
        print(f"\n─── {mode} Results ({model_name}) ───")
        print(f"  Severity accuracy:  {counts['severity']}/{n} ({100*counts['severity']/n:.0f}%)")
        print(f"  Urgency accuracy:   {counts['urgency']}/{n} ({100*counts['urgency']/n:.0f}%)")
        print(f"  Triage category:    {counts['category']}/{n} ({100*counts['category']/n:.0f}%)")
        print(f"  Concern identified: {counts['concern']}/{n} ({100*counts['concern']/n:.0f}%)")

    return results


# ═══════════════════════════════════════════════════════════════════════════════
# Compare all saved results
# ═══════════════════════════════════════════════════════════════════════════════

def compare_all():
    """Load all saved results and produce cross-model comparison."""
    print("\n╔══════════════════════════════════════════════════════════════════╗")
    print("║           Cross-Model CHW Triage Comparison                   ║")
    print("╚══════════════════════════════════════════════════════════════════╝\n")

    if not RESULTS_DIR.exists():
        print("No results found. Run benchmarks first.")
        return

    files = sorted(RESULTS_DIR.glob("*.json"))
    if not files:
        print("No result files found in", RESULTS_DIR)
        return

    all_data = []
    for f in files:
        with open(f) as fh:
            data = json.load(fh)
            all_data.append(data)

    # Sensor-augmented comparison
    print("─── Part A: Sensor-Augmented Triage Category Accuracy ───\n")
    print(f"  {'Model':<35} {'Severity':>10} {'Category':>10} {'Urgency':>10} {'Concern':>10}")
    print(f"  {'─'*78}")
    for data in all_data:
        model = data["model"]
        sa = data.get("sensor_augmented", [])
        valid = [r for r in sa if not r.get("error")]
        n = len(valid)
        if n == 0:
            continue
        sev = sum(1 for r in valid if r["correct"]["severity"])
        cat = sum(1 for r in valid if r["correct"]["category"])
        urg = sum(1 for r in valid if r["correct"]["urgency"])
        con = sum(1 for r in valid if r["correct"]["concern"])
        print(f"  {model:<35} {sev:>3}/{n} ({100*sev/n:>3.0f}%) "
              f"{cat:>3}/{n} ({100*cat/n:>3.0f}%) "
              f"{urg:>3}/{n} ({100*urg/n:>3.0f}%) "
              f"{con:>3}/{n} ({100*con/n:>3.0f}%)")

    # Text-only comparison
    print(f"\n─── Part B: Text-Only Triage Category Accuracy ───\n")
    print(f"  {'Model':<35} {'Severity':>10} {'Category':>10} {'Urgency':>10} {'Concern':>10}")
    print(f"  {'─'*78}")
    for data in all_data:
        model = data["model"]
        to = data.get("text_only", [])
        valid = [r for r in to if not r.get("error")]
        n = len(valid)
        if n == 0:
            continue
        sev = sum(1 for r in valid if r["correct"]["severity"])
        cat = sum(1 for r in valid if r["correct"]["category"])
        urg = sum(1 for r in valid if r["correct"]["urgency"])
        con = sum(1 for r in valid if r["correct"]["concern"])
        print(f"  {model:<35} {sev:>3}/{n} ({100*sev/n:>3.0f}%) "
              f"{cat:>3}/{n} ({100*cat/n:>3.0f}%) "
              f"{urg:>3}/{n} ({100*urg/n:>3.0f}%) "
              f"{con:>3}/{n} ({100*con/n:>3.0f}%)")

    # Sensor impact delta
    print(f"\n─── Sensor Data Impact (Δ Category Accuracy) ───\n")
    for data in all_data:
        model = data["model"]
        sa = [r for r in data.get("sensor_augmented", []) if not r.get("error")]
        to = [r for r in data.get("text_only", []) if not r.get("error")]
        n_sa, n_to = len(sa), len(to)
        if n_sa == 0 or n_to == 0:
            continue
        sa_acc = 100 * sum(1 for r in sa if r["correct"]["category"]) / n_sa
        to_acc = 100 * sum(1 for r in to if r["correct"]["category"]) / n_to
        delta = sa_acc - to_acc
        arrow = "↑" if delta > 0 else "↓" if delta < 0 else "→"
        print(f"  {model:<35} {arrow} {delta:+.0f}pp  ({sa_acc:.0f}% sensor vs {to_acc:.0f}% text-only)")

    print()


# ═══════════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Nku CHW Triage Benchmark")
    parser.add_argument("--model", type=str, default="medgemma-4b-q4_k_m",
                        help="Model name (used for result filename)")
    parser.add_argument("--port", type=int, default=8787,
                        help="llama-server port (default: 8787)")
    parser.add_argument("--compare", action="store_true",
                        help="Compare all saved results (no server needed)")
    args = parser.parse_args()

    print("\n╔══════════════════════════════════════════════════════════════════╗")
    print("║           Nku CHW Triage Benchmark                            ║")
    print("║           Sensor-Augmented vs Text-Only × All Models          ║")
    print("╚══════════════════════════════════════════════════════════════════╝\n")

    if args.compare:
        compare_all()
        return

    if not check_server(args.port):
        print(f"⛔ llama-server not available on localhost:{args.port}")
        print(f"   Start server with the model and re-run.")
        print(f"\n   Example:")
        print(f"   llama-server -m medgemma-4b-q4_k_m.gguf --port {args.port} -ngl 99")
        print(f"\n   Then: python benchmark/nku_medgemma_benchmark.py --model {args.model} --port {args.port}")
        return

    print(f"Model: {args.model}")
    print(f"Server: localhost:{args.port}")

    # Part A: Sensor-augmented
    sensor_results = run_triage(args.model, args.port, include_sensors=True)

    # Part B: Text-only
    text_results = run_triage(args.model, args.port, include_sensors=False)

    # Sensor impact summary
    sa_valid = [r for r in sensor_results if not r.get("error")]
    to_valid = [r for r in text_results if not r.get("error")]
    if sa_valid and to_valid:
        sa_cat = 100 * sum(1 for r in sa_valid if r["correct"]["category"]) / len(sa_valid)
        to_cat = 100 * sum(1 for r in to_valid if r["correct"]["category"]) / len(to_valid)
        delta = sa_cat - to_cat
        print(f"\n{'=' * 78}")
        print(f"SENSOR IMPACT on {args.model}")
        print(f"{'=' * 78}")
        print(f"  Sensor-Augmented: {sa_cat:.0f}% triage category accuracy")
        print(f"  Text-Only:        {to_cat:.0f}% triage category accuracy")
        if delta > 0:
            print(f"  → Sensors improve triage by +{delta:.0f}pp")
        elif delta == 0:
            print(f"  → No difference (sensor data doesn't change triage)")
        else:
            print(f"  → Text-only is better by {-delta:.0f}pp (unexpected)")

    # Save results
    RESULTS_DIR.mkdir(exist_ok=True)
    output = {
        "model": args.model,
        "port": args.port,
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "n_vignettes": len(CHW_VIGNETTES),
        "sensor_augmented": sensor_results,
        "text_only": text_results,
    }
    out_path = RESULTS_DIR / f"{args.model}.json"
    with open(out_path, "w") as f:
        json.dump(output, f, indent=2)
    print(f"\nResults saved to {out_path}")
    print(f"Run --compare after benchmarking all models to see cross-model table.")


if __name__ == "__main__":
    main()
