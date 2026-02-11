from sentence_transformers import SentenceTransformer, util
import numpy as np
import logging

# Setup Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def verify_ddc_threshold():
    """Light-weight verification script for similarity threshold calibration."""
    logger.info("Initializing Verification Tool (NKU-DDC-VERIFY)...")
    model = SentenceTransformer('BAAI/bge-small-en-v1.5')
    
    # Placeholder Centroid (In a real run, this is calculated from gold standard records)
    # The Consultant brief provides the anchor logic.
    # We will use a representative "Real-World" clinical sample as a proxy for the centroid for calibration.
    anchor_sample = "Patient presents with yellowing of the sclera and fever. Resides in Ashanti region."
    anchor_v = model.encode(anchor_sample)
    
    # Test Samples
    test_cases = [
        ("Clinically Relevant", "Subject shows signs of jaundice and high temperature. Local clinic notes mention malaria-endemic zone."),
        ("Medical but Unrelated", "Patient has a broken radius from a fall. No signs of infection."),
        ("Non-Medical Noise", "The quick brown fox jumps over the lazy dog."),
        ("Synthetic Edge Case", "Synthetically generated medical report: Patient has yellow eyes and warm skin. Ashanti local.")
    ]
    
    logger.info("--- CALIBRATION RESULTS ---")
    for category, text in test_cases:
        sample_v = model.encode(text)
        similarity = util.cos_sim(anchor_v, sample_v)
        status = "RETAINED" if similarity.item() > 0.82 else "PRUNED"
        logger.info(f"[{category}] Similarity: {similarity.item():.4f} -> {status}")

if __name__ == "__main__":
    verify_ddc_threshold()
