#!/usr/bin/env python3
"""
macOS TFLite Conversion Patch

This module patches the ai_edge_litert and litert_torch libraries to work on macOS
by providing mock implementations for the broken native TFLite interpreter bindings.

The actual TFLite conversion still works because it uses TensorFlow's TFLiteConverter
which is properly packaged for macOS.

Usage:
    import macos_tflite_patch  # MUST be first import
    macos_tflite_patch.apply_patches()

    # Now you can use litert_torch
    import litert_torch
    edge_model = litert_torch.convert(model, (sample_input,))
"""

import sys
import types
from unittest.mock import MagicMock

def create_mock_module(name):
    """Create a mock module that can be imported."""
    mock = types.ModuleType(name)
    return mock


def apply_patches():
    """Apply all necessary patches for macOS compatibility."""

    print("🍎 Applying macOS TFLite compatibility patches...")

    # 1. Mock ai_edge_litert.interpreter module
    # This is the primary module with broken native bindings
    mock_interpreter = create_mock_module('ai_edge_litert.interpreter')

    class MockInterpreter:
        """Mock TFLite Interpreter for macOS compatibility.

        This mock is only used when loading/running .tflite models at inference time.
        The actual conversion process uses TensorFlow's TFLiteConverter which works.
        """
        def __init__(self, model_path=None, model_content=None, **kwargs):
            self._model_path = model_path
            self._model_content = model_content
            self._allocated = False
            self._input_details = []
            self._output_details = []

        def allocate_tensors(self):
            self._allocated = True

        def get_input_details(self):
            return self._input_details

        def get_output_details(self):
            return self._output_details

        def set_tensor(self, index, value):
            pass

        def get_tensor(self, index):
            import numpy as np
            return np.zeros((1,))

        def invoke(self):
            pass

        def resize_tensor_input(self, index, shape, strict=False):
            pass

    mock_interpreter.Interpreter = MockInterpreter
    mock_interpreter.load_delegate = MagicMock(return_value=None)

    # 2. Mock the _pywrap modules that have broken .so files
    mock_wrapper = create_mock_module('ai_edge_litert._pywrap_tensorflow_interpreter_wrapper')
    mock_wrapper.InterpreterWrapper = MagicMock
    mock_wrapper.CreateInterpreter = MagicMock

    # 3. Mock aot modules
    mock_aot = create_mock_module('ai_edge_litert.aot')
    mock_aot_compile = create_mock_module('ai_edge_litert.aot.aot_compile')
    mock_aot_core = create_mock_module('ai_edge_litert.aot.core')
    mock_aot_types = create_mock_module('ai_edge_litert.aot.core.types')
    mock_aot_vendors = create_mock_module('ai_edge_litert.aot.vendors')
    mock_import_vendor = create_mock_module('ai_edge_litert.aot.vendors.import_vendor')
    mock_fallback = create_mock_module('ai_edge_litert.aot.vendors.fallback_backend')

    # Mock types - comprehensive coverage for all litert_types
    class MockBackendId:
        CPU = "cpu"
        GPU = "gpu"
        NPU = "npu"

    class MockTarget:
        """Mock compilation target."""
        def __init__(self, backend_id=None, **kwargs):
            self.backend_id = backend_id or MockBackendId.CPU

    class MockConfig:
        """Mock configuration dict-like."""
        pass

    class MockCompilationConfig:
        def __init__(self, target=None, **kwargs):
            self.target = target or MockTarget()
            for k, v in kwargs.items():
                setattr(self, k, v)

    class MockCompilationResult:
        """Mock result from compilation."""
        def __init__(self, model_bytes=None):
            self._model_bytes = model_bytes or bytes()
            self.metadata = {}

        def tflite_model(self):
            return self._model_bytes

    class MockModel:
        """Mock LiteRT model."""
        def __init__(self, model_bytes=None):
            self._bytes = model_bytes or bytes()

        @classmethod
        def create_from_bytes(cls, model_bytes):
            return cls(model_bytes)

        def tflite_model(self):
            return self._bytes

    class MockBufferRef:
        pass

    class MockExecInfo:
        pass

    mock_aot_types.BackendId = MockBackendId
    mock_aot_types.Target = MockTarget
    mock_aot_types.Config = MockConfig
    mock_aot_types.BufferRef = MockBufferRef
    mock_aot_types.ExecInfo = MockExecInfo
    mock_aot_types.CompilationConfig = MockCompilationConfig
    mock_aot_types.CompilationResult = MockCompilationResult
    mock_aot_types.TensorBufferRequirements = MagicMock
    mock_aot_types.CompiledModel = MagicMock
    mock_aot_types.Model = MockModel

    mock_aot_compile.aot_compile = MagicMock(return_value=bytes())
    mock_import_vendor.import_vendor = MagicMock()
    mock_fallback.FallbackBackend = MagicMock

    # 4. Mock internal modules
    mock_internal = create_mock_module('ai_edge_litert.internal')
    mock_litertlm = create_mock_module('ai_edge_litert.internal.litertlm_builder')
    mock_llm_meta = create_mock_module('ai_edge_litert.internal.llm_metadata_pb2')
    mock_llm_type = create_mock_module('ai_edge_litert.internal.llm_model_type_pb2')
    mock_sampler = create_mock_module('ai_edge_litert.internal.sampler_params_pb2')

    mock_litertlm.LiteRtLmBuilder = MagicMock
    mock_llm_meta.LlmMetadata = MagicMock
    mock_llm_type.LlmModelType = MagicMock
    mock_sampler.SamplerParams = MagicMock

    # 5. Mock tools modules
    mock_tools = create_mock_module('ai_edge_litert.tools')
    mock_model_utils = create_mock_module('ai_edge_litert.tools.model_utils')
    mock_mu_core = create_mock_module('ai_edge_litert.tools.model_utils.core')
    mock_mu_match = create_mock_module('ai_edge_litert.tools.model_utils.match')
    mock_mu_mlir = create_mock_module('ai_edge_litert.tools.model_utils.dialect.mlir')
    mock_mu_tfl = create_mock_module('ai_edge_litert.tools.model_utils.dialect.tfl')

    # Register all mocks in sys.modules
    mocks = {
        'ai_edge_litert.interpreter': mock_interpreter,
        'ai_edge_litert._pywrap_tensorflow_interpreter_wrapper': mock_wrapper,
        'ai_edge_litert.aot': mock_aot,
        'ai_edge_litert.aot.aot_compile': mock_aot_compile,
        'ai_edge_litert.aot.core': mock_aot_core,
        'ai_edge_litert.aot.core.types': mock_aot_types,
        'ai_edge_litert.aot.vendors': mock_aot_vendors,
        'ai_edge_litert.aot.vendors.import_vendor': mock_import_vendor,
        'ai_edge_litert.aot.vendors.fallback_backend': mock_fallback,
        'ai_edge_litert.internal': mock_internal,
        'ai_edge_litert.internal.litertlm_builder': mock_litertlm,
        'ai_edge_litert.internal.llm_metadata_pb2': mock_llm_meta,
        'ai_edge_litert.internal.llm_model_type_pb2': mock_llm_type,
        'ai_edge_litert.internal.sampler_params_pb2': mock_sampler,
        'ai_edge_litert.tools': mock_tools,
        'ai_edge_litert.tools.model_utils': mock_model_utils,
        'ai_edge_litert.tools.model_utils.core': mock_mu_core,
        'ai_edge_litert.tools.model_utils.match': mock_mu_match,
        'ai_edge_litert.tools.model_utils.dialect': create_mock_module('ai_edge_litert.tools.model_utils.dialect'),
        'ai_edge_litert.tools.model_utils.dialect.mlir': mock_mu_mlir,
        'ai_edge_litert.tools.model_utils.dialect.tfl': mock_mu_tfl,
    }

    for name, mock in mocks.items():
        sys.modules[name] = mock

    print("✅ Patches applied successfully!")
    print("   - ai_edge_litert.interpreter: MOCKED")
    print("   - ai_edge_litert.aot.*: MOCKED")
    print("   - ai_edge_litert.internal.*: MOCKED")
    print("   - ai_edge_litert.tools.*: MOCKED")
    print("")
    print("⚠️  Note: TFLite model CONVERSION will work (uses TensorFlow)")
    print("⚠️  Note: TFLite model INFERENCE requires real interpreter")


def test_patches():
    """Test that patches work correctly."""
    apply_patches()

    print("\n🧪 Testing import chain...")

    try:
        # This should now work without the native library error
        from litert_torch._convert.converter import convert
        print("✅ litert_torch.convert imported successfully!")
        return True
    except ImportError as e:
        print(f"❌ Import failed: {e}")
        return False


if __name__ == "__main__":
    test_patches()
