"""
Configuration tests for cloud inference defaults.
"""

import unittest
import sys
import os

# Add project root to path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../cloud/inference_api')))


class TestModelConfigDefaults(unittest.TestCase):
    """Ensure cloud model defaults are deployable by default."""

    def test_translategemma_defaults_are_not_placeholders(self):
        from cloud.inference_api.config import ModelConfig

        cfg = ModelConfig()
        # Regression guard: avoid non-resolvable placeholder defaults.
        self.assertNotEqual(cfg.translategemma_repo, "google/translategemma")
        self.assertNotEqual(cfg.translategemma_file, "translategemma-4b.gguf")
        self.assertTrue(cfg.translategemma_repo)
        self.assertTrue(cfg.translategemma_file)


if __name__ == '__main__':
    unittest.main(verbosity=2)
