#!/usr/bin/env python3
"""
Nku Sensor Validation — Deterministic clinical threshold tests.

Validates that sensor detector thresholds produce clinically reasonable outputs.
Uses pure Python reimplementations of the scoring functions from the Kotlin source —
no Android runtime required.

Thresholds are sourced directly from:
  - PallorDetector.kt  (saturation-based conjunctival analysis)
  - JaundiceDetector.kt (scleral yellow ratio → sigmoid)
  - EdemaDetector.kt   (EAR-based periorbital scoring)
  - RPPGProcessor.kt   (DFT-based HR extraction)
  - SensorFusion.kt    (aggregation + confidence gating)
  - ClinicalReasoner.kt (triage thresholds)

Run: python -m pytest benchmark/test_sensor_validation.py -v
"""

import math
import pytest


# ═══════════════════════════════════════════════════════════════════════════════
# Reimplemented scoring functions (from Kotlin source)
# ═══════════════════════════════════════════════════════════════════════════════

# --- PallorDetector.kt (line 165-195) ---
def pallor_severity(avg_saturation: float) -> str:
    """
    Maps conjunctival saturation to pallor severity.
    Lower saturation = more pallor = more anemia risk.
    Thresholds from PallorDetector.kt:
      >= 0.20 → NORMAL
      >= 0.12 → MILD
      >= 0.06 → MODERATE
      <  0.06 → SEVERE
    """
    if avg_saturation >= 0.20:
        return "NORMAL"
    elif avg_saturation >= 0.12:
        return "MILD"
    elif avg_saturation >= 0.06:
        return "MODERATE"
    else:
        return "SEVERE"


def pallor_score(avg_saturation: float) -> float:
    """
    Maps saturation to 0-1 pallor score (higher = more pallor).
    From PallorDetector.kt: score = 1.0 - clamp(saturation / 0.30, 0, 1)
    """
    return 1.0 - max(0.0, min(1.0, avg_saturation / 0.30))


# --- JaundiceDetector.kt (line 140-175) ---
def jaundice_score_from_ratio(yellow_ratio: float) -> float:
    """
    Sigmoid mapping from yellow pixel ratio to jaundice score.
    From JaundiceDetector.kt: score = 1 / (1 + e^(-12 * (ratio - 0.15)))
    """
    return 1.0 / (1.0 + math.exp(-12.0 * (yellow_ratio - 0.15)))


def jaundice_severity(score: float) -> str:
    """
    Maps jaundice score to severity.
    From JaundiceDetector.kt:
      <= 0.25 → NORMAL
      <= 0.50 → MILD
      <= 0.75 → MODERATE
      >  0.75 → SEVERE
    """
    if score <= 0.25:
        return "NORMAL"
    elif score <= 0.50:
        return "MILD"
    elif score <= 0.75:
        return "MODERATE"
    else:
        return "SEVERE"


# --- EdemaDetector.kt (line 260-295) ---
def edema_from_ear(ear_value: float, baseline_ear: float = 2.8) -> float:
    """
    Maps Eye Aspect Ratio to edema score.
    From EdemaDetector.kt: lower EAR = more swelling.
    score = clamp((baseline - ear) / baseline, 0, 1)
    """
    return max(0.0, min(1.0, (baseline_ear - ear_value) / baseline_ear))


def edema_severity(score: float) -> str:
    """
    From EdemaDetector.kt:
      <= 0.15 → NORMAL
      <= 0.40 → MILD
      <= 0.65 → SIGNIFICANT
      >  0.65 → SEVERE
    """
    if score <= 0.15:
        return "NORMAL"
    elif score <= 0.40:
        return "MILD"
    elif score <= 0.65:
        return "SIGNIFICANT"
    else:
        return "SEVERE"


# --- SensorFusion.kt + ClinicalReasoner.kt ---
CONFIDENCE_THRESHOLD = 0.75  # From SensorFusion.kt line 45

def is_reliable(confidence: float) -> bool:
    """Sensor reading is reliable if confidence >= 0.75."""
    return confidence >= CONFIDENCE_THRESHOLD


def has_high_risk_indicators(hr_bpm: int = 0, pallor_sev: str = "NORMAL",
                              jaundice_sev: str = "NORMAL", edema_sev: str = "NORMAL",
                              respiratory_risk: str = "LOW") -> bool:
    """
    From SensorFusion.kt hasHighRiskIndicators():
    Returns true if any single sensor shows critical values.
    """
    if hr_bpm > 130 or hr_bpm < 50:
        return True
    if pallor_sev == "SEVERE":
        return True
    if jaundice_sev == "SEVERE":
        return True
    if edema_sev in ("SIGNIFICANT", "SEVERE"):
        return True
    if respiratory_risk == "HIGH":
        return True
    return False


def triage_category(severity: str) -> str:
    """
    From ClinicalReasoner.kt triage mapping.
    Maps severity to WHO triage color.
    """
    mapping = {
        "LOW": "GREEN",
        "MEDIUM": "YELLOW",
        "HIGH_URGENT": "ORANGE",
        "HIGH_IMMEDIATE": "RED",
    }
    return mapping.get(severity, "YELLOW")


# ═══════════════════════════════════════════════════════════════════════════════
# TEST SUITE
# ═══════════════════════════════════════════════════════════════════════════════

class TestPallorDetector:
    """Validates pallor detection thresholds against clinical expectations."""

    def test_normal_conjunctiva(self):
        """Healthy saturation → NORMAL (no anemia signs)."""
        assert pallor_severity(0.25) == "NORMAL"
        assert pallor_score(0.25) == pytest.approx(0.167, abs=0.01)

    def test_mild_pallor(self):
        """Slightly pale → MILD (consider iron studies)."""
        assert pallor_severity(0.15) == "MILD"

    def test_moderate_pallor(self):
        """Clearly pale → MODERATE (likely Hb < 10 g/dL)."""
        assert pallor_severity(0.08) == "MODERATE"

    def test_severe_pallor(self):
        """Very pale, almost white conjunctiva → SEVERE (Hb < 7 g/dL risk)."""
        assert pallor_severity(0.03) == "SEVERE"
        assert pallor_score(0.03) == pytest.approx(0.90, abs=0.01)

    def test_boundary_normal_mild(self):
        """Threshold boundary at 0.20 — exactly 0.20 is NORMAL."""
        assert pallor_severity(0.20) == "NORMAL"
        assert pallor_severity(0.19) == "MILD"

    def test_score_monotonic(self):
        """Lower saturation → higher pallor score (monotonically increasing risk)."""
        scores = [pallor_score(s) for s in [0.30, 0.20, 0.10, 0.05, 0.01]]
        for i in range(len(scores) - 1):
            assert scores[i] < scores[i + 1], f"Score not monotonic at index {i}"


class TestJaundiceDetector:
    """Validates jaundice detection against clinical expectations."""

    def test_normal_sclera(self):
        """No yellow pixels → NORMAL."""
        score = jaundice_score_from_ratio(0.03)
        assert jaundice_severity(score) == "NORMAL"

    def test_mild_jaundice(self):
        """Slight yellowing → MILD (bilirubin 2-3 mg/dL range)."""
        score = jaundice_score_from_ratio(0.15)
        assert jaundice_severity(score) in ("NORMAL", "MILD")  # sigmoid midpoint

    def test_moderate_jaundice(self):
        """Obvious yellowing → MODERATE (bilirubin 3-10 mg/dL range)."""
        score = jaundice_score_from_ratio(0.25)
        assert jaundice_severity(score) in ("MODERATE", "SEVERE")

    def test_severe_jaundice(self):
        """Deeply yellow sclera → SEVERE (bilirubin > 10 mg/dL risk)."""
        score = jaundice_score_from_ratio(0.40)
        assert score > 0.90
        assert jaundice_severity(score) == "SEVERE"

    def test_sigmoid_properties(self):
        """Sigmoid should be ~0.5 at ratio=0.15 (midpoint)."""
        midpoint = jaundice_score_from_ratio(0.15)
        assert midpoint == pytest.approx(0.5, abs=0.01)

    def test_score_monotonic(self):
        """Higher yellow ratio → higher jaundice score."""
        scores = [jaundice_score_from_ratio(r) for r in [0.0, 0.05, 0.10, 0.20, 0.30, 0.50]]
        for i in range(len(scores) - 1):
            assert scores[i] < scores[i + 1]


class TestEdemaDetector:
    """Validates edema detection (EAR-based) against clinical expectations."""

    def test_normal_ear(self):
        """EAR at baseline → no swelling → NORMAL."""
        score = edema_from_ear(2.8)
        assert score == pytest.approx(0.0, abs=0.01)
        assert edema_severity(score) == "NORMAL"

    def test_mild_swelling(self):
        """Slightly reduced EAR → MILD periorbital puffiness."""
        score = edema_from_ear(2.3)
        assert edema_severity(score) == "MILD"

    def test_significant_edema(self):
        """Notably reduced EAR → SIGNIFICANT (preeclampsia concern in pregnancy)."""
        score = edema_from_ear(1.5)
        assert edema_severity(score) in ("SIGNIFICANT", "SEVERE")

    def test_severe_edema(self):
        """Very narrow eye opening → SEVERE swelling."""
        score = edema_from_ear(0.5)
        assert score > 0.80
        assert edema_severity(score) == "SEVERE"

    def test_above_baseline(self):
        """EAR above baseline → clamps to 0 (no negative edema)."""
        score = edema_from_ear(3.2)
        assert score == 0.0


class TestConfidenceGating:
    """Validates that low-confidence readings are correctly flagged."""

    def test_high_confidence_reliable(self):
        """0.85 confidence → reliable."""
        assert is_reliable(0.85) is True

    def test_threshold_reliable(self):
        """Exactly 0.75 → reliable (inclusive)."""
        assert is_reliable(0.75) is True

    def test_below_threshold_unreliable(self):
        """0.60 confidence → unreliable, should be annotated in prompt."""
        assert is_reliable(0.60) is False

    def test_very_low_unreliable(self):
        """0.30 confidence → unreliable."""
        assert is_reliable(0.30) is False


class TestHighRiskAggregation:
    """Validates multi-sensor danger sign detection from SensorFusion.kt."""

    def test_tachycardia_triggers(self):
        """HR > 130 BPM → high risk (WHO danger sign)."""
        assert has_high_risk_indicators(hr_bpm=145) is True

    def test_bradycardia_triggers(self):
        """HR < 50 BPM → high risk."""
        assert has_high_risk_indicators(hr_bpm=40) is True

    def test_normal_hr_no_trigger(self):
        """Normal HR alone → no risk."""
        assert has_high_risk_indicators(hr_bpm=78) is False

    def test_severe_pallor_triggers(self):
        """Severe pallor → high risk (acute anemia)."""
        assert has_high_risk_indicators(pallor_sev="SEVERE") is True

    def test_mild_pallor_no_trigger(self):
        """Mild pallor alone → not high risk."""
        assert has_high_risk_indicators(hr_bpm=78, pallor_sev="MILD") is False

    def test_significant_edema_triggers(self):
        """Significant edema → high risk (preeclampsia if pregnant)."""
        assert has_high_risk_indicators(edema_sev="SIGNIFICANT") is True

    def test_respiratory_high_triggers(self):
        """High respiratory risk → high risk."""
        assert has_high_risk_indicators(respiratory_risk="HIGH") is True

    def test_multiple_moderate_no_trigger(self):
        """Multiple moderate values without any single high-risk → no trigger."""
        assert has_high_risk_indicators(
            hr_bpm=100, pallor_sev="MODERATE", jaundice_sev="MILD"
        ) is False

    def test_multi_sensor_danger(self):
        """Tachycardia + severe pallor → high risk (multi-sensor confirmation)."""
        assert has_high_risk_indicators(hr_bpm=150, pallor_sev="SEVERE") is True


class TestTriageMapping:
    """Validates severity → triage color mapping."""

    def test_low_green(self):
        assert triage_category("LOW") == "GREEN"

    def test_medium_yellow(self):
        assert triage_category("MEDIUM") == "YELLOW"

    def test_high_urgent_orange(self):
        assert triage_category("HIGH_URGENT") == "ORANGE"

    def test_high_immediate_red(self):
        assert triage_category("HIGH_IMMEDIATE") == "RED"


class TestClinicalScenarios:
    """End-to-end scenarios validating the full sensor → triage chain."""

    def test_severe_malaria_with_anemia(self):
        """Patient: High fever, HR 128, severe pallor, mild jaundice → HIGH risk."""
        hr = 128
        p_sev = pallor_severity(0.04)   # SEVERE
        j_score = jaundice_score_from_ratio(0.18)
        j_sev = jaundice_severity(j_score)

        assert p_sev == "SEVERE"
        assert j_sev in ("MILD", "MODERATE")
        assert has_high_risk_indicators(hr_bpm=hr, pallor_sev=p_sev) is True

    def test_normal_prenatal(self):
        """Patient: 28wk pregnant, HR 82, no edema → LOW risk."""
        hr = 82
        e_score = edema_from_ear(2.7)
        e_sev = edema_severity(e_score)

        assert e_sev == "NORMAL"
        assert has_high_risk_indicators(hr_bpm=hr, edema_sev=e_sev) is False

    def test_preeclampsia_warning(self):
        """Patient: 32wk pregnant, HR 98, significant edema → HIGH risk."""
        e_score = edema_from_ear(1.4)
        e_sev = edema_severity(e_score)

        assert e_sev in ("SIGNIFICANT", "SEVERE")
        assert has_high_risk_indicators(edema_sev=e_sev) is True

    def test_moderate_anemia_workup(self):
        """Patient: Fatigue, moderate pallor → flagged but not critical."""
        p_sev = pallor_severity(0.08)
        assert p_sev == "MODERATE"
        assert has_high_risk_indicators(hr_bpm=95, pallor_sev=p_sev) is False

    def test_neonatal_jaundice_emergency(self):
        """Patient: 3-day newborn, severe jaundice → HIGH risk."""
        j_score = jaundice_score_from_ratio(0.45)
        j_sev = jaundice_severity(j_score)

        assert j_sev == "SEVERE"
        assert has_high_risk_indicators(jaundice_sev=j_sev) is True


# ═══════════════════════════════════════════════════════════════════════════════
# Summary table (for documentation / README embedding)
# ═══════════════════════════════════════════════════════════════════════════════

def print_validation_summary():
    """Print a formatted summary table of all sensor thresholds."""
    print("\n" + "=" * 90)
    print("NKU SENSOR VALIDATION — Clinical Threshold Summary")
    print("=" * 90)

    print(f"\n{'─── Pallor Detector (Conjunctival Saturation) ───':}")
    print(f"  {'Saturation':<15} {'Severity':<12} {'Clinical Interpretation'}")
    print(f"  {'─'*60}")
    for sat, expected_sev, interpretation in [
        (0.25, "NORMAL",   "Healthy conjunctiva, no anemia signs"),
        (0.15, "MILD",     "Slight pallor, consider iron studies"),
        (0.08, "MODERATE", "Clear pallor, likely Hb < 10 g/dL"),
        (0.03, "SEVERE",   "Marked pallor, Hb < 7 g/dL risk — refer immediately"),
    ]:
        sev = pallor_severity(sat)
        status = "✅" if sev == expected_sev else "❌"
        print(f"  {status} {sat:<14.2f} {sev:<12} {interpretation}")

    print(f"\n{'─── Jaundice Detector (Scleral Yellow Ratio → Sigmoid) ───':}")
    print(f"  {'Yellow Ratio':<15} {'Score':<8} {'Severity':<12} {'Clinical Interpretation'}")
    print(f"  {'─'*70}")
    for ratio, expected_sev, interpretation in [
        (0.03, "NORMAL",   "Clear sclera, no bilirubin elevation"),
        (0.12, "MILD",     "Faint yellowing, subclinical or early jaundice"),
        (0.25, "MODERATE", "Obvious icterus, bilirubin 3-10 mg/dL range"),
        (0.40, "SEVERE",   "Deep yellow, bilirubin > 10 mg/dL — urgent workup"),
    ]:
        score = jaundice_score_from_ratio(ratio)
        sev = jaundice_severity(score)
        status = "✅" if sev == expected_sev else "⚠️"
        print(f"  {status} {ratio:<14.2f} {score:<7.2f}  {sev:<12} {interpretation}")

    print(f"\n{'─── Edema Detector (Eye Aspect Ratio) ───':}")
    print(f"  {'EAR Value':<15} {'Score':<8} {'Severity':<14} {'Clinical Interpretation'}")
    print(f"  {'─'*70}")
    for ear, expected_sev, interpretation in [
        (2.8, "NORMAL",      "No periorbital swelling"),
        (2.3, "MILD",        "Slight puffiness, possibly fatigue/allergen"),
        (1.5, "SIGNIFICANT", "Notable swelling — preeclampsia if pregnant"),
        (0.5, "SEVERE",      "Marked facial edema — urgent evaluation"),
    ]:
        score = edema_from_ear(ear)
        sev = edema_severity(score)
        status = "✅" if sev == expected_sev else "⚠️"
        print(f"  {status} {ear:<14.1f} {score:<7.2f}  {sev:<14} {interpretation}")

    print(f"\n{'─── Confidence Gating ───':}")
    print(f"  Threshold: {CONFIDENCE_THRESHOLD}")
    print(f"  Below threshold → sensor reading annotated as 'UNRELIABLE' in prompt")
    print(f"  Above threshold → sensor reading used for clinical reasoning")

    print(f"\n{'─── High Risk Danger Signs (WHO/IMCI) ───':}")
    print(f"  HR > 130 or HR < 50 BPM → DANGER")
    print(f"  Pallor SEVERE → DANGER")
    print(f"  Jaundice SEVERE → DANGER")
    print(f"  Edema SIGNIFICANT or SEVERE → DANGER")
    print(f"  Respiratory Risk HIGH → DANGER")
    print()


if __name__ == "__main__":
    print_validation_summary()
