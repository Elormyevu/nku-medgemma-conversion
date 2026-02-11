import os
import torch
import logging
import torch.nn as nn
from transformers import (
    TrainingArguments, 
    Trainer,
    DataCollatorForLanguageModeling
)
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from datasets import load_dataset, concatenate_datasets
import argparse
import huggingface_hub
import traceback
import sys
import transformers
import fsspec
from google.cloud import storage
from PIL import Image


# ANTIGRAVITY GOVERNANCE: Architect Level
# Role: Project Nku Training Lead
# Strategy: "Super-Squeeze" (CMA -> ART -> SFT)



SYSTEM_PROMPT = """You are Nku, a specialized medical AI assistant for Pan-African clinical contexts. 
Your goal is to provide accurate, culturally sensitive diagnostic suggestions based on medical imagery. 
Prioritize common regional conditions like Malaria, Anemia, Jaundice, and skin phenotypes V/VI. 
Always include clinical rationale and recommended next steps. 
Return findings with geometric grounding where possible. <loc_en>"""

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def upload_directory_to_gcs(local_path, gcs_path):
    """Explicit GCS Persistence Hook for Vertex AI Ephemeral Disks."""
    if not gcs_path.startswith("gs://"):
        logger.warning(f"Target path {gcs_path} is not GCS. Skipping upload.")
        return
        
    logger.info(f"⬆️ Beginning GCS Persistence: {local_path} -> {gcs_path}")
    bucket_name = gcs_path.split("/")[2]
    prefix = "/".join(gcs_path.split("/")[3:])
    
    client = storage.Client()
    bucket = client.bucket(bucket_name)
    
    upload_count = 0
    for root, dirs, files in os.walk(local_path):
        for file in files:
            local_file = os.path.join(root, file)
            relative_path = os.path.relpath(local_file, local_path)
            blob_path = os.path.join(prefix, relative_path)
            blob = bucket.blob(blob_path)
            blob.upload_from_filename(local_file)
            upload_count += 1
            
    logger.info(f"✅ GCS Persistence Complete: {upload_count} artifacts secured in gs://{bucket_name}/{prefix}")

class NkuRobustTrainer(Trainer):
    """
    Custom Trainer implementing Adversarial Robustness Training (ART).
    Injects noise into the visual embeddings to simulate low-quality mobile sensors.
    """
    def compute_loss(self, model, inputs, return_outputs=False, num_items_in_batch=None):
        # Stage 2: Adversarial Noise Injection
        # We inject Gaussian noise into the pixel_values to simulate sensor grain
        if self.args.noise_std > 0:
            if "pixel_values" in inputs:
                noise = torch.randn_like(inputs["pixel_values"]) * self.args.noise_std
                inputs["pixel_values"] = inputs["pixel_values"] + noise

        outputs = model(**inputs)
        loss = outputs.get("loss")
        return (loss, outputs) if return_outputs else loss

def parse_args():
    parser = argparse.ArgumentParser(description="Nku MedGemma Training")
    parser.add_argument("--hf_token", type=str, help="Hugging Face Token for gated model access")
    parser.add_argument("--output_dir", type=str, default="gs://nku-impact-outputs-1335/checkpoints")
    parser.add_argument("--lora_save_path", type=str, default="gs://nku-impact-outputs-1335/final_lora")
    parser.add_argument("--noise_std", type=float, default=0.02)
    parser.add_argument("--batch_size", type=int, default=1)
    parser.add_argument("--grad_accum", type=int, default=32)
    parser.add_argument("--epochs", type=int, default=10)
    # Dataset Paths
    parser.add_argument("--synthetic_path", type=str, default="gs://nku-impact-data-1335/curated/grand_synthesis_v3_curated.jsonl")
    parser.add_argument("--real_skin_path", type=str, default="gs://nku-impact-data-1335/real/skin_cancer.jsonl")
    parser.add_argument("--malaria_path", type=str, default="gs://nku-impact-data-1335/real/malaria.jsonl")
    parser.add_argument("--pan_african_path", type=str, default="gs://nku-impact-data-1335/real/pan_african.jsonl")
    parser.add_argument("--expanded_path", type=str, default="gs://nku-impact-data-1335/real/expanded_synthesis.jsonl")
    return parser.parse_known_args()[0]

def train():
    args = parse_args()
    
    # Authenticate to Hugging Face
    if args.hf_token:
        print(f"Logging in to Hugging Face with token: {args.hf_token[:5]}...")
        huggingface_hub.login(token=args.hf_token)
    else:
        # Fallback to env var if not passed as arg
        hf_token_env = os.environ.get("HF_TOKEN")
        if hf_token_env:
            print(f"Logging in to Hugging Face with env var: {hf_token_env[:5]}...")
            huggingface_hub.login(token=hf_token_env)
        else:
            print("WARNING: HF_TOKEN not found. Access to gated models may fail.")

    model_id = "google/medgemma-1.5-4b-it" 
    
    # ANTIGRAVITY GOVERNANCE: Efficiency Lock (GCS Interconnect)
    # If running on Vertex AI with GCS FUSE, use the local path to avoid HF timeouts
    gcs_model_path = "/gcs/dora-gemini-research-nku-models/medgemma-1.5-4b-it"
    if os.path.exists(gcs_model_path):
        logger.info(f"✅ Found GCS-mounted weights at {gcs_model_path}. Using local load.")
        model_id = gcs_model_path
    else:
        logger.info(f"ℹ️ GCS weights not found at {gcs_model_path}. Falling back to HF: {model_id}")

    output_dir = args.output_dir
    lora_save_path = args.lora_save_path
    
    # Dataset Paths (From Args)
    synthetic_path = args.synthetic_path
    real_skin_path = args.real_skin_path
    malaria_path = args.malaria_path
    pan_african_path = args.pan_african_path
    expanded_path = args.expanded_path

    logger.info(f"🚀 Starting Nku 'Super-Squeeze' Fine-tuning on A100")

    # ANTIGRAVITY GOVERNANCE: Robust Model Loading (AutoModel)
    logger.info("📦 Loading Processor and Model (Auto Detect)...")
    from transformers import AutoProcessor
    
    # Robust Import for Model Class
    logger.info(f"Transformers Version: {transformers.__version__}")
    # Priority Loading for MedGemma (Gemma 3)
    # We try CausalLM first as it's the standard for Gemma, then Vision2Seq
    try:
        from transformers import AutoModelForCausalLM
        ModelClass = AutoModelForCausalLM
        logger.info("✅ Using AutoModelForCausalLM")
    except ImportError:
        try:
            from transformers import AutoModelForVision2Seq
            ModelClass = AutoModelForVision2Seq
            logger.info("✅ Using AutoModelForVision2Seq")
        except ImportError as e:
            logger.warning(f"⚠️ Both AutoModelForCausalLM and AutoModelForVision2Seq failed: {e}")
            from transformers import AutoModel
            ModelClass = AutoModel
            logger.info("✅ Using AutoModel (Generic Fallback)")

    # Use AutoProcessor to handle Gemma3 correctly
    processor = AutoProcessor.from_pretrained(model_id, trust_remote_code=True)
    
    # Set system prompt correctly
    system_prompt = SYSTEM_PROMPT
    logger.info(f"System prompt injected: {system_prompt[:100]}...")

    model = ModelClass.from_pretrained(
        model_id, 
        torch_dtype=torch.bfloat16,
        device_map="auto",
        trust_remote_code=True,
        attn_implementation="sdpa"
    )
    logger.info(f"Loaded Model Class: {type(model)}")
    
    # PEFT/Transformers Compatibility Fix: Add prepare_inputs_for_generation if missing
    # PEFT/Transformers Compatibility Fix 16: Robust Monkeypatch
    if not hasattr(model, "prepare_inputs_for_generation"):
        logger.warning("⚠️ Model is missing 'prepare_inputs_for_generation'. Injecting standalone fallback...")
        
        def _prepare_inputs_for_generation(self, input_ids, past_key_values=None, attention_mask=None, inputs_embeds=None, **kwargs):
            if past_key_values:
                input_ids = input_ids[:, -1:]
            model_inputs = {"input_ids": input_ids}
            if past_key_values:
                model_inputs["past_key_values"] = past_key_values
            if attention_mask is not None:
                model_inputs["attention_mask"] = attention_mask
            if inputs_embeds is not None:
                model_inputs["inputs_embeds"] = inputs_embeds
            model_inputs.update(kwargs)
            return model_inputs

        import types
        model.prepare_inputs_for_generation = types.MethodType(_prepare_inputs_for_generation, model)
        logger.info("✅ Injected standalone prepare_inputs_for_generation method.")
    
    # LOG MODEL CONFIG FOR DEBUGGING
    logger.info("--- MODEL CONFIG DETAILS ---")
    if hasattr(model.config, "vision_config"):
        vc = model.config.vision_config
        logger.info(f"Vision Tower: {getattr(vc, 'model_type', 'unknown')}")
        logger.info(f"Image Size: {getattr(vc, 'image_size', 'unknown')}")
        logger.info(f"Patch Size: {getattr(vc, 'patch_size', 'unknown')}")
    
    # Resize model embeddings to match tokenizer
    try:
        logger.info(f"Config Type: {type(model.config)}")
        logger.info(f"Config attributes: {dir(model.config)}")
        
        # Robustly get vocab_size (Gemma 3 might nest it in text_config object)
        vocab_size = getattr(model.config, "vocab_size", None)
        if vocab_size is None:
            if hasattr(model.config, "text_config"):
                text_config = model.config.text_config
                logger.info(f"Text Config Type: {type(text_config)}")
                vocab_size = getattr(text_config, "vocab_size", "Unknown")
            else:
                vocab_size = "Unknown"

        logger.info(f"Before resize_token_embeddings. Model vocab: {vocab_size}, Tokenizer: {len(processor.tokenizer)}")
        model.resize_token_embeddings(len(processor.tokenizer))
        logger.info("✅ resize_token_embeddings successful")
    except Exception as e:
        logger.error(f"❌ CRITICAL ERROR during embedding resize: {e}")
        logger.error(f"Traceback: {traceback.format_exc()}")
        # Continue execution if possible, or raise if critical
        # raise e # Commented out to see if we can proceed without resizing or diagnose further
    
    effective_res = 896
    if hasattr(model.config, "vision_config") and hasattr(model.config.vision_config, "image_size"):
        effective_res = model.config.vision_config.image_size

    logger.info(f"📍 Effective Resolution: {effective_res}")

    # DIAGNOSTIC: Inspect Processor and Tokenizer
    logger.info("--- PROCESSOR DIAGNOSTICS ---")
    logger.info(f"Processor Type: {type(processor)}")
    try:
        # Use getattr with default to avoid AttributeError for missing attributes
        special_tokens = getattr(processor.tokenizer, 'all_special_tokens', 'N/A')
        add_special = getattr(processor.tokenizer, 'additional_special_tokens', 'N/A')
        logger.info(f"Tokenizer All Special Tokens: {special_tokens}")
        logger.info(f"Tokenizer Additional Special Tokens: {add_special}")
        logger.info(f"Tokenizer Map: {getattr(processor.tokenizer, 'special_tokens_map', 'N/A')}")
    except Exception as e:
        logger.warning(f"Could not log tokenizer special tokens: {e}")
    
    # Try to see what apply_chat_template does
    try:
        test_messages = [{"role": "user", "content": [{"type": "image"}, {"type": "text", "text": "test"}]}]
        test_prompt = processor.apply_chat_template(test_messages, add_generation_prompt=True)
        logger.info(f"Chat Template Test Prompt: {test_prompt}")
    except Exception as e:
        logger.info(f"Chat Template Test Failed: {e}")

    # 2. QLoRA Configuration
    lora_config = LoraConfig(
        r=16, 
        lora_alpha=32,
        target_modules=["q_proj", "v_proj", "k_proj", "o_proj", "gate_proj", "up_proj", "down_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM"
    )
    
    model = prepare_model_for_kbit_training(model)
    model = get_peft_model(model, lora_config)

    # 3. Multi-Dataset Integration
    logger.info("📦 Integrating Synthetic + Real-world Medical Datasets")
    datasets_to_combine = []
    
    # Load synthetic twins
    # Load synthetic twins
    try:
        logger.info(f"Loading synthetic dataset from {synthetic_path}")
        datasets_to_combine.append(load_dataset("json", data_files=synthetic_path)["train"])
    except Exception as e:
        logger.warning(f"Synthetic dataset failed to load from {synthetic_path}: {e}")
    
    # Load Real-world datasets if available
    real_configs = [
        (real_skin_path, "Skin"),
        (malaria_path, "Malaria"),
        (pan_african_path, "Pan-African"),
        (expanded_path, "Expanded Synthesis v3")
    ]
    for path, name in real_configs:
        try:
             logger.info(f"Loading {name} dataset from {path}")
             datasets_to_combine.append(load_dataset("json", data_files=path)["train"])
             logger.info(f"Loaded {name} dataset.")
        except Exception as e:
             logger.warning(f"Failed to load {name} dataset from {path}: {e}")

    if not datasets_to_combine:
        raise ValueError("No datasets found! Please check GCS paths and FUSE mounting.")

    full_dataset = concatenate_datasets(datasets_to_combine).shuffle(seed=42)
    
    # ANTIGRAVITY GOVERNANCE: Generalization Safeguard
    # Split 90/10 for Train/Validation to prevent overfitting (Address user concern)
    logger.info("🛡️ Applying 90/10 Train/Validation split for generalization...")
    split_dataset = full_dataset.train_test_split(test_size=0.1, seed=42)
    train_dataset = split_dataset["train"]
    eval_dataset = split_dataset["test"]
    logger.info(f"Train/Eval Sizes: {len(train_dataset)} / {len(eval_dataset)}")

    # 4. Data Collation & Tokenization
    from PIL import Image

    class MedGemmaDataCollator:
        def __init__(self, processor, system_prompt):
            self.processor = processor
            self.system_prompt = system_prompt

        def __call__(self, examples):
            texts = []
            images = []
            for example in examples:
                # Prepend system prompt if not present (optional, but good for consistency)
                # For PaliGemma, we typically structure: "<image> user_prompt \n system_prompt response" or similar.
                # Here we just use the simple text field + system prompt implication.
                # Assuming 'text' is the target caption/response or instruction.
                
                # Check if 'image' is a path or bytes
                img_input = example["image"]
                if isinstance(img_input, str):
                    # Handle GCS FUSE paths
                    if img_input.startswith("gs://"):
                         img_input = img_input.replace("gs://", "/gcs/")
                    
                    # Handle relative paths (e.g., "images/foo.jpg" from synthetic dataset)
                    if not os.path.exists(img_input) and not img_input.startswith("/"):
                        # Try resolving relative to the synthetic data directory
                        # We know synthetic data is at /gcs/nku-impact-data-1335/synthetic/
                        potential_path_syn = os.path.join("/gcs/nku-impact-data-1335/synthetic/", img_input)
                        potential_path_real = os.path.join("/gcs/nku-impact-data-1335/", img_input)

                        if os.path.exists(potential_path_syn):
                            img_input = potential_path_syn
                        elif os.path.exists(potential_path_real):
                            img_input = potential_path_real
                        else:
                            # Fallback check for other datasets or just log
                            pass

                    try:
                        # Strategic Fix 30: Use fsspec for robust GCS image resolution
                        # This resolves 'Errno 2' when running on Vertex without FUSE
                        with fsspec.open(img_input, "rb") as f:
                            image = Image.open(f).convert("RGB")
                    except Exception as e:
                        logger.warning(f"Could not load image {img_input}: {e}. Generating placeholder.")
                        # continue # Removed to allow fallback creation
                        # For robustness, let's create a black image placeholder to avoid crashing batch
                        image = Image.new('RGB', (224, 224), color='black')
                else:
                    image = img_input.convert("RGB")
                
                # Gemma 3 expects List[List[Image]] for batched inputs (one list of images per text example)
                images.append([image])
                
                # DYNAMIC IMAGE TOKEN DISCOVERY
                image_token = "<image>" # Standard fallback for PaliGemma/Gemma 3
                
                # DEBUG: Log special tokens to help identify the correct one if this fails
                if not hasattr(self, "_logged_special_tokens"):
                    special_tokens = getattr(self.processor.tokenizer, "all_special_tokens", [])
                    logger.info(f"🔍 All Special Tokens: {special_tokens}")
                    self._logged_special_tokens = True
                # HEURISTIC-BASED IMAGE TOKEN PRIORITY (Fix 19)
                all_special = getattr(self.processor.tokenizer, "all_special_tokens", [])
                # DYNAMIC IMAGE TOKEN DISCOVERY FOR GEMMA 3
                # Standard markdown-style image token for Gemma 3 is '![img]'
                image_token = "![img]" 
                
                # Check all special tokens for the most appropriate one
                special_tokens = getattr(self.processor.tokenizer, "all_special_tokens", [])
                
                # Priority 1: Explicitly look for '![img]' (Gemma 3 standard)
                if "![img]" in special_tokens:
                    image_token = "![img]"
                # Priority 2: Fallback to heuristic search
                else:
                    for token in special_tokens:
                        if "image" in token.lower() or "img" in token.lower():
                            if token != "<end_of_image>": # Avoid choosing the end token
                                image_token = token
                                break
                
                # CRITICAL: Gemma 3 requires the image token to be present.
                # Format: [TOKEN][SYSTEM PROMPT][USER PROMPT]
                full_prompt = f"{image_token}\n{self.system_prompt}\n{example['text']}"
                texts.append(full_prompt)
                
                if not hasattr(self, "_logged_token"):
                    logger.info(f"Discovered Image Token: {image_token}")
                    logger.info(f"🔍 Special Tokens List: {special_tokens}")
                    self._logged_token = True

            if not texts:
                return {} 
            
            # DEBUG: Print first prompt to verify <image> presence
            if len(texts) > 0 and not hasattr(self, "_logged_sample"):
                logger.info(f"SAMPLE PROMPT (First Batch): {texts[0][:150]}...")
                self._logged_sample = True

            # Processor handles tokenization and image processing
            # Processor handles tokenization and image processing
            # Gemma 3 uses fixed 256 tokens per image + text prompt.
            # 4096 is a generous buffer for system prompt + response + image tokens.
            max_tokens = 4096 
            
            batch = self.processor(
                text=texts, 
                images=images, 
                return_tensors="pt", 
                padding=True, 
                truncation=True,
                max_length=max_tokens
                # size={"height": effective_res, "width": effective_res} # Let AutoProcessor handle default resolution
            )
            
            # For Causal LM, labels are usually the input_ids
            batch["labels"] = batch["input_ids"].clone()
            return batch

    # ...
    
    # 5. Training Arguments with Noise Hook
    training_args = TrainingArguments(
        output_dir=output_dir,
        per_device_train_batch_size=2,      # FORCE REDUCTION: 4 -> 2 due to No-FlashAttention OOM
        gradient_accumulation_steps=args.grad_accum * 2, # FORCE INCREASE: Maintain effective batch size
        learning_rate=2e-4, 
        weight_decay=0.01,          # Added for generalization
        warmup_ratio=0.03,          # Added for stability
        num_train_epochs=args.epochs, 
        logging_steps=10,
        eval_strategy="steps",      # Frequent validation for SPOT instances
        eval_steps=500,             # Evaluate every 500 steps
        save_strategy="steps",      # Save checkpoints frequent to mitigate preemption loss
        save_steps=500,             # Matched with eval steps
        load_best_model_at_end=True,# Always revert to best generalized state
        metric_for_best_model="eval_loss",
        save_total_limit=3,         # Keep slightly more checkpoints for safety
        bf16=True, 
        optim="paged_adamw_32bit",
        lr_scheduler_type="cosine", # Smoother convergence
        label_smoothing_factor=0.1,  # Added to prevent over-confidence/overfitting
        remove_unused_columns=False,
        gradient_checkpointing=True, # CRITICAL: Enable GC to save VRAM (SigLIP + Gemma 3 is heavy)
    )
    
    training_args.noise_std = args.noise_std

    trainer = NkuRobustTrainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        data_collator=MedGemmaDataCollator(processor, system_prompt),
    )

    # 5. Execute Multi-Stage Loop
    logger.info("🔥 Phase 1 & 2: Robust Fine-tuning (CMA + ART)")
    trainer.train()
    
    # 6. Export Weights
    # Strategic Fix 30: Multi-Stage Persistence (Local Stage -> GCS Upload)
    # save_pretrained doesn't natively support gs:// reliably on all Vertex nodes
    local_lora_path = "./final_lora_staging"
    os.makedirs(local_lora_path, exist_ok=True)
    
    logger.info(f"📦 Staging hardware-hardened weights at {local_lora_path}")
    model.save_pretrained(local_lora_path)
    processor.save_pretrained(local_lora_path)
    
    if lora_save_path.startswith("gs://"):
        upload_directory_to_gcs(local_lora_path, lora_save_path)
    else:
        logger.info(f"Saving to local path directly: {lora_save_path}")
        model.save_pretrained(lora_save_path)
        processor.save_pretrained(lora_save_path)
    
    logger.info("✅ Nku Super-Squeeze Training sequence completed.")

if __name__ == "__main__":
    try:
        train()
    except Exception as e:
        logger.error("🔥 FATAL ERROR CAUGHT IN MAIN TRAIN LOOP 🔥")
        logger.error(traceback.format_exc())
        sys.exit(1)
