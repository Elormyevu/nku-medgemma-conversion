#!/usr/bin/env python3
"""
MedGemma 1.5 4B TFLite Conversion Script
Designed to run in Docker with torch_xla support (Linux).
Falls back to mocking on macOS for testing purposes.
"""
import sys
import os
import types
from unittest.mock import MagicMock
import importlib.machinery

# Detect if we're running in Docker/Linux with real torch_xla
def has_real_torch_xla():
    try:
        import torch_xla
        # Check if it's a real module, not our mock
        return hasattr(torch_xla, '__file__') and torch_xla.__file__ is not None
    except ImportError:
        return False

USE_REAL_XLA = has_real_torch_xla()
print(f"🔧 Environment: {'Linux/Docker (real torch_xla)' if USE_REAL_XLA else 'macOS (mocked torch_xla)'}")

# Only apply mocking if we don't have real torch_xla
if not USE_REAL_XLA:
    print("⚠️  Mocking torch_xla - final TFLite export may fail without real XLA backend")

    class AttributeAwareMock(types.ModuleType):
        def __init__(self, name):
            super().__init__(name)
            self.__path__ = []
            self.__spec__ = importlib.machinery.ModuleSpec(name, None)

        def __getattr__(self, name):
            return MagicMock(name=f"mock.{name}")

    class ForgeryMock(AttributeAwareMock):
        def __init__(self, name):
            super().__init__(name)

        def exported_program_to_stablehlo(self, *args, **kwargs):
            bundle = MagicMock()
            bundle.state_dict = {}
            bundle.additional_constants = []

            mock_func = MagicMock()
            mock_func.meta.name = "forward"
            mock_func.meta.input_locations = []
            mock_func.meta.input_signature = []
            mock_func.meta.output_signature = [MagicMock(dtype="float32", shape=[1, 256000])]
            mock_func.bytecode = b""

            bundle.stablehlo_funcs = [mock_func]

            result = MagicMock()
            result._bundle = bundle
            return result

        def merge_stablehlo_bundles(self, *args, **kwargs):
            gm = MagicMock()
            gm._bundle = args[0][0] if args and args[0] else MagicMock()
            return gm

    mock_xla = AttributeAwareMock("torch_xla")
    shlo_forgery = ForgeryMock("torch_xla.stablehlo")
    mock_xla.stablehlo = shlo_forgery
    mock_xla.core = AttributeAwareMock("torch_xla.core")
    mock_xla.utils = AttributeAwareMock("torch_xla.utils")
    mock_xla.experimental = AttributeAwareMock("torch_xla.experimental")

    sys.modules["torch_xla"] = mock_xla
    sys.modules["torch_xla.core"] = mock_xla.core
    sys.modules["torch_xla.core.xla_model"] = AttributeAwareMock("torch_xla.core.xla_model")
    sys.modules["torch_xla.utils"] = mock_xla.utils
    sys.modules["torch_xla.utils.utils"] = AttributeAwareMock("torch_xla.utils.utils")
    sys.modules["torch_xla.experimental"] = mock_xla.experimental
    sys.modules["torch_xla.experimental.xla_marker"] = AttributeAwareMock("torch_xla.experimental.xla_marker")
    sys.modules["torch_xla.experimental.xla_mlir_debuginfo"] = AttributeAwareMock("torch_xla.experimental.xla_mlir_debuginfo")
    sys.modules["torch_xla.experimental.mark_pattern_utils"] = AttributeAwareMock("torch_xla.experimental.mark_pattern_utils")
    sys.modules["torch_xla.stablehlo"] = shlo_forgery

import torch

# Register fake XLA ops if needed (both real and mock environments may need this)
try:
    from torch.library import Library, impl
    lib = Library("xla", "DEF")
    lib.define("mark_tensor(Tensor self) -> Tensor")
    lib.define("write_mlir_debuginfo(Tensor self, Tensor other, int index) -> Tensor")

    @impl(lib, "mark_tensor", "CompositeExplicitAutograd")
    def mark_tensor(self):
        return self

    @impl(lib, "write_mlir_debuginfo", "CompositeExplicitAutograd")
    def write_mlir_debuginfo(self, self_tensor, other_tensor, index):
        return self_tensor
except Exception as e:
    print(f"Info: XLA ops registration: {e}")

from transformers import AutoModelForImageTextToText, AutoProcessor, AutoConfig
import ai_edge_torch

# Patch PassBase to skip debuginfo pass (crashes on some systems)
try:
    from torch.fx.passes.infra.pass_base import PassBase, PassResult
    original_pass_call = PassBase.__call__

    def patched_pass_call(self, *args, **kwargs):
        if type(self).__name__ == "InjectMlirDebuginfoPass":
            gm = args[0] if args else kwargs.get('graph_module')
            return PassResult(gm, True)
        return original_pass_call(self, *args, **kwargs)

    PassBase.__call__ = patched_pass_call
    print("✅ PassBase patched to intercept debuginfo pass")
except Exception as e:
    print(f"Warning: Could not patch PassBase: {e}")

# Patch autocast to avoid metadata issues during export
class dummy_autocast:
    def __init__(self, *args, **kwargs): pass
    def __enter__(self): pass
    def __exit__(self, *args, **kwargs): pass
    def __call__(self, func): return func

torch.autocast = dummy_autocast
if hasattr(torch, 'cuda') and hasattr(torch.cuda, 'amp'):
    torch.cuda.amp.autocast = dummy_autocast
if hasattr(torch, 'amp'):
    torch.amp.autocast = dummy_autocast

from ai_edge_torch.generative.quantize import quant_recipes, quant_recipe_utils
from ai_edge_torch.quantize import quant_config
from tqdm import tqdm
import time

# Transformers / Gemma 3 patches for export compatibility
try:
    from transformers import masking_utils
    simple_mapping = {"eager": "eager", "sdpa": "sdpa", "flash_attention_2": "flash_attention_2"}
    if hasattr(masking_utils, "ALL_MASK_ATTENTION_FUNCTIONS"):
        masking_utils.ALL_MASK_ATTENTION_FUNCTIONS._global_mapping = simple_mapping

    def dummy_create_causal_mask(input_ids=None, *args, **kwargs):
        device = torch.device('cpu')
        seq_len = input_ids.shape[-1] if input_ids is not None else 1
        return torch.tril(torch.ones((seq_len, seq_len), device=device, dtype=torch.bool))[None, None, :, :]

    def dummy_create_sliding_window_mask(input_ids=None, *args, **kwargs):
        device = torch.device('cpu')
        seq_len = input_ids.shape[-1] if input_ids is not None else 1
        return torch.tril(torch.ones((seq_len, seq_len), device=device, dtype=torch.bool))[None, None, :, :]

    masking_utils.create_causal_mask = dummy_create_causal_mask
    masking_utils.create_sliding_window_causal_mask = dummy_create_sliding_window_mask

    import transformers.models.gemma3.modeling_gemma3 as g3_mod

    def TraceableOutput(**kwargs):
        val = kwargs.get('logits', kwargs.get('last_hidden_state'))
        if val is None and kwargs:
            val = next(iter(kwargs.values()))
        return (val,) if val is not None else ()

    g3_mod.BaseModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3ModelOutputWithPast = TraceableOutput
    g3_mod.Gemma3CausalLMOutputWithPast = TraceableOutput

    def patched_rope_forward(self, x, position_ids):
        inv_freq_expanded = self.inv_freq[None, :, None].float().expand(position_ids.shape[0], -1, 1).to(x.device)
        position_ids_expanded = position_ids[:, None, :].float()
        freqs = (inv_freq_expanded.float() @ position_ids_expanded.float()).transpose(1, 2)
        emb = torch.cat((freqs, freqs), dim=-1)
        cos = emb.cos() * self.attention_scaling
        sin = emb.sin() * self.attention_scaling
        return cos.to(dtype=x.dtype), sin.to(dtype=x.dtype)

    g3_mod.Gemma3RotaryEmbedding.forward = patched_rope_forward
    print("✅ Gemma 3 output and RoPE patches applied")
except Exception as e:
    print(f"Warning: Could not patch Gemma 3: {e}")


class LanguageModelWrapper(torch.nn.Module):
    """Wraps the language model to return only logits for export compatibility."""
    def __init__(self, model):
        super().__init__()
        if hasattr(model, 'language_model'):
            self.model = model.language_model
        else:
            self.model = model

    def forward(self, input_ids):
        outputs = self.model(input_ids=input_ids, use_cache=False)
        if isinstance(outputs, (list, tuple)) and len(outputs) > 0:
            return outputs[0]
        return outputs


def convert_with_progress(model_id, output_path):
    print(f"\n🚀 Starting TFLite Conversion for {model_id}")
    print(f"📁 Output path: {output_path}\n")

    pbar = tqdm(total=100, desc="Overall Progress", bar_format="{l_bar}{bar:40}{r_bar}")

    # 1. Load Model (15%)
    pbar.update(5)
    pbar.set_description("Loading MedGemma Model")
    try:
        model = AutoModelForImageTextToText.from_pretrained(
            model_id,
            torch_dtype=torch.float32,
            low_cpu_mem_usage=True,
            trust_remote_code=True,
            attn_implementation="eager"
        )
        model.eval()
    except Exception as e:
        pbar.close()
        import traceback
        traceback.print_exc()
        print(f"❌ Failed to load model: {e}")
        return False

    pbar.update(10)

    lm_wrapper = LanguageModelWrapper(model)

    # 2. Prepare Dummy Input (5%)
    pbar.set_description("Preparing Inputs")
    dummy_input = torch.randint(0, 256000, (1, 64))
    pbar.update(5)

    # 3. Configure Quantization (5%)
    pbar.set_description("Configuring Quantization")
    try:
        layer_quant = quant_recipe_utils.create_layer_quant_int8_dynamic()
        quant_cfg = quant_recipes.full_linear_int8_dynamic_recipe()
        print("\n✅ Using INT8 dynamic quantization")
    except:
        print("\n⚠️ INT8 not available, falling back to FP16")
        quant_cfg = quant_recipe_utils.create_config_from_recipe(
            quant_recipe_utils.create_layer_quant_fp16()
        )
    pbar.update(5)

    # 4. Conversion (60%)
    pbar.set_description("Converting to TFLite")
    try:
        with torch.no_grad():
            edge_model = ai_edge_torch.convert(
                lm_wrapper,
                (dummy_input,),
                quant_config=quant_cfg
            )
    except Exception as e:
        pbar.close()
        import traceback
        traceback.print_exc()
        print(f"\n❌ Conversion failed: {e}")
        if not USE_REAL_XLA:
            print("\n💡 This is expected on macOS. Run this script in Docker for real conversion:")
            print("   ./run_conversion.sh")
        return False
    pbar.update(60)

    # 5. Export (15%)
    pbar.set_description("Exporting TFLite")
    os.makedirs(os.path.dirname(output_path) or '.', exist_ok=True)
    edge_model.export(output_path)
    pbar.update(15)
    pbar.set_description("✅ Complete")
    pbar.close()

    size_gb = os.path.getsize(output_path) / (1024**3)
    print(f"\n📊 Final Asset: {output_path}")
    print(f"📦 Size: {size_gb:.2f} GB")
    return True


if __name__ == "__main__":
    # Determine output path - use /output in Docker, current dir otherwise
    if os.path.exists("/output"):
        output_dir = "/output"
    elif os.environ.get("GITHUB_ACTIONS"):
        output_dir = "."
    else:
        output_dir = "."

    output_path = os.path.join(output_dir, "medgemma_int4.tflite")

    success = convert_with_progress(
        model_id="google/medgemma-1.5-4b-it",
        output_path=output_path
    )

    sys.exit(0 if success else 1)
