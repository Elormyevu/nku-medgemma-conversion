"""
Configuration tests for cloud inference defaults.
"""

import unittest
import sys
import os
from unittest.mock import patch

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

    def test_from_env_preserves_default_revisions_when_unset(self):
        from cloud.inference_api.config import AppConfig, ModelConfig

        with patch.dict(os.environ, {}, clear=True):
            cfg = AppConfig.from_env()

        defaults = ModelConfig()
        self.assertEqual(cfg.model.medgemma_revision, defaults.medgemma_revision)
        self.assertEqual(cfg.model.translategemma_revision, defaults.translategemma_revision)

    def test_from_env_allows_revision_override(self):
        from cloud.inference_api.config import AppConfig

        with patch.dict(os.environ, {
            'MEDGEMMA_REVISION': 'custom-med',
            'TRANSLATEGEMMA_REVISION': 'custom-trans',
        }, clear=True):
            cfg = AppConfig.from_env()

        self.assertEqual(cfg.model.medgemma_revision, 'custom-med')
        self.assertEqual(cfg.model.translategemma_revision, 'custom-trans')


if __name__ == '__main__':
    unittest.main(verbosity=2)
