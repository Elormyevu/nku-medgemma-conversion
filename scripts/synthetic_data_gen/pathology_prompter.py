import random
import json

class PathologyPrompter:
    """
    Generates medically accurate text prompts for diffusion models (Stable Diffusion, Imagen)
    to create synthetic pathology textures.
    
    Simulates MedGemma's multimodal understanding by translating clinical grades
    into visual texture descriptions.
    """
    
    def __init__(self):
        # We focus exclusively on West African phenotypes for this project
        self.skin_tones = [
            "Fitzpatrick Skin Type V (Deep Brown)", 
            "Fitzpatrick Skin Type VI (Very Deep Brown/Black)"
        ]
        
        self.lighting_conditions = [
            "studio lighting",
            "harsh sunlight", 
            "dim fluorescent clinic lighting",
            "natural window light"
        ]

    def _get_random_context(self):
        return {
            "skin_tone": random.choice(self.skin_tones),
            "lighting": random.choice(self.lighting_conditions)
        }

    def generate_anemia_prompt(self, severity_grade):
        """
        Generates a prompt for Palpebral Conjunctiva Anemia.
        Severity Grade: 0 (Normal) to 3 (Severe/Critical).
        """
        context = self._get_random_context()
        
        base_prompt = (
            f"Macro photography of the human eye, specifically the lower palpebral conjunctiva. "
            f"Subject has {context['skin_tone']}. {context['lighting']}. "
            "High resolution, medical reference quality, 8k, sharp focus. "
        )
        
        if severity_grade == 0:
            # Normal
            pathology = (
                "The conjunctiva is rich red and highly vascularized. "
                "Healthy hemoglobin levels. Distinct capillaries visible. "
                "Moist mucous membrane."
            )
        elif severity_grade == 1:
            # Mild
            pathology = (
                "The conjunctiva shows slight pallor but retains pink coloration/erythema. "
                "Reduced vascular contrast. "
            )
        elif severity_grade == 2:
            # Moderate
            pathology = (
                "The conjunctiva is pale with reduced redness. "
                "Erythema is mottled. Significant loss of capillary definition. "
            )
        elif severity_grade == 3:
            # Severe (Critical Malaria Risk)
            pathology = (
                "The conjunctiva is extremely pale, waxy, and almost white. "
                "Severe anemia. Complete loss of vascular redness. "
                "Paper-white appearance of the inner eyelid."
            )
        else:
            raise ValueError("Severity grade must be 0-3")
            
        return base_prompt + pathology

    def generate_jaundice_prompt(self, severity_grade):
        """
        Generates a prompt for Scleral Icterus (Jaundice).
        Severity: 0 (None) to 3 (Deep Yellow).
        """
        context = self._get_random_context()
        base_prompt = (
            f"Close-up medical portrait of the eye. "
            f"Subject has {context['skin_tone']}. {context['lighting']}. "
            "Realistic texture, 8k."
        )
        
        if severity_grade == 0:
            pathology = "The sclera (white of the eye) is clear, porcelain white/off-white. No discoloration."
        elif severity_grade == 1:
            pathology = "Trace yellowing at the periphery of the sclera. Mild icterus."
        elif severity_grade == 2:
            pathology = "The sclera is visibly yellow. Distinct jaundice. Meaningful bilirubin deposition."
        elif severity_grade == 3:
            pathology = "The sclera is deep, saturated mustard yellow. Severe icterus. Advanced hepatic dysfunction."
            
        return base_prompt + pathology

    def batch_generate(self, condition, count=5):
        prompts = []
        for _ in range(count):
            # Randomize severity
            severity = random.randint(0, 3)
            if condition == "anemia":
                p = self.generate_anemia_prompt(severity)
            elif condition == "jaundice":
                p = self.generate_jaundice_prompt(severity)
            prompts.append({"condition": condition, "severity": severity, "prompt": p})
        return prompts

if __name__ == "__main__":
    # Test the prompter
    prompter = PathologyPrompter()
    print("--- Anemia Prompts ---")
    for p in prompter.batch_generate("anemia", 3):
        print(f"[Grade {p['severity']}]: {p['prompt']}")
        print("")
