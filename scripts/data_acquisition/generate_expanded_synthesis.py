import json
import os

OUTPUT_PATH = "rescue_data/real/expanded_synthesis.jsonl"
os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)

SAMPLES = [
    # === Pediatric (PASSION/Sub-Saharan Africa) ===
    {
        "image": "real/pan_african/pediatric_fungal_1.jpg",
        "text": "Pediatric patient from Sub-Saharan clinical outreach. Clinical presentation: Scale-like lesion on scalp. Suspected Tinea Capitis. Recommendation: Antifungal treatment and hygiene education. <loc_en>"
    },
    {
        "image": "real/pan_african/pediatric_eczema_1.jpg",
        "text": "Pediatric patient (Pigmented Skin Type VI). Clinical presentation: Erythematous plaques on antecubital fossae. Diagnosis: Atopic Dermatitis. Management: Topical steroids and emollients. <loc_en>"
    },
    
    # === Maternal Health (5-Country African Ultrasound) ===
    {
        "image": "real/pan_african/maternal_ultrasound_1.jpg",
        "text": "Obstetric ultrasound from rural African clinic (2nd Trimester). View: Fetal Head (BPD). Measurements within normal range for gestational age. No obvious neural tube defects. <loc_en>"
    },
    {
        "image": "real/pan_african/maternal_ultrasound_2.jpg",
        "text": "Obstetric ultrasound (3rd Trimester). View: Placental location and amniotic fluid volume. Placenta is fundal and posterior. AFI is 12cm (normal). <loc_en>"
    },

    # === Molecular Bridge (Bindr Lung Atlas HLCA) ===
    {
        "image": "synthetic/concept_anchor_lung.jpg", 
        "text": "Molecular Lung Profiling (HLCA Subset). Domain: Alveolar Type II Cells. Key marker: SFTPC. Clinical relevance: Surfactant production in neonatal respiratory distress. <loc_en>"
    },
    {
        "image": "synthetic/concept_anchor_lung_fibrosis.jpg",
        "text": "Molecular Lung Profiling (Fibrotic Atlas). Domain: Myofibroblasts. Key marker: POSTN/COL1A1. Clinical correlate: Idiopathic Pulmonary Fibrosis (IPF) progression. <loc_en>"
    }
]

def generate():
    with open(OUTPUT_PATH, 'w') as f:
        for sample in SAMPLES:
            f.write(json.dumps(sample) + "\n")
    print(f"✅ Generated {len(SAMPLES)} expanded synthesis samples in {OUTPUT_PATH}")

if __name__ == "__main__":
    generate()
