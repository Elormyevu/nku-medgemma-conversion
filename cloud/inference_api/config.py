"""
Nku Configuration Module
Centralized, type-safe configuration with environment variable loading.
"""

import os
from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class ModelConfig:
    """Model configuration settings."""
    # D-04: Cloud uses Q2_K quantization (higher quality, ~1.6GB per model) because
    # Cloud Run instances have 8GB RAM. Mobile uses IQ1_M (~0.78GB) for 2GB-RAM
    # budget devices. This is an intentional quality/size trade-off, not a mismatch.
    medgemma_repo: str = "wredd/MedGemma-1.5-4B-PT-GGUF"
    medgemma_file: str = "MedGemma-1.5-4B-PT-Q2_K.gguf"
    translategemma_repo: str = "wredd/TranslateGemma-4B-GGUF"
    translategemma_file: str = "TranslateGemma-4B-Q2_K.gguf"
    context_size: int = 2048
    n_batch: int = 512
    n_threads: int = 4
    n_gpu_layers: int = 0  # CPU-only for Cloud Run


@dataclass
class RateLimitConfig:
    """Rate limiting configuration."""
    requests_per_minute: int = 30
    requests_per_hour: int = 500


@dataclass
class SecurityConfig:
    """Security configuration."""
    allowed_origins: List[str] = field(default_factory=list)
    max_text_length: int = 2000
    max_symptom_length: int = 1000
    enable_rate_limiting: bool = True


@dataclass
class InferenceConfig:
    """Inference behavior configuration."""
    translation_temperature: float = 0.3
    triage_temperature: float = 0.2
    max_translation_tokens: int = 256
    max_triage_tokens: int = 512


@dataclass
class AppConfig:
    """Main application configuration."""
    # Environment
    env: str = "production"
    debug: bool = False
    port: int = 8080
    log_level: str = "INFO"
    log_json: bool = True

    # Sub-configurations
    model: ModelConfig = field(default_factory=ModelConfig)
    rate_limit: RateLimitConfig = field(default_factory=RateLimitConfig)
    security: SecurityConfig = field(default_factory=SecurityConfig)
    inference: InferenceConfig = field(default_factory=InferenceConfig)

    @classmethod
    def from_env(cls) -> 'AppConfig':
        """Load configuration from environment variables."""

        # Parse allowed origins from comma-separated list
        allowed_origins_str = os.environ.get('ALLOWED_ORIGINS', '')
        allowed_origins = [o.strip() for o in allowed_origins_str.split(',') if o.strip()]

        return cls(
            env=os.environ.get('APP_ENV', 'production'),
            debug=os.environ.get('DEBUG', '').lower() == 'true',
            port=int(os.environ.get('PORT', 8080)),
            log_level=os.environ.get('LOG_LEVEL', 'INFO'),
            log_json=os.environ.get('LOG_JSON', 'true').lower() == 'true',

            model=ModelConfig(
                medgemma_repo=os.environ.get('MEDGEMMA_REPO', 'wredd/MedGemma-1.5-4B-PT-GGUF'),
                medgemma_file=os.environ.get('MEDGEMMA_FILE', 'MedGemma-1.5-4B-PT-Q2_K.gguf'),
                translategemma_repo=os.environ.get('TRANSLATEGEMMA_REPO', 'wredd/TranslateGemma-4B-GGUF'),
                translategemma_file=os.environ.get('TRANSLATEGEMMA_FILE', 'TranslateGemma-4B-Q2_K.gguf'),
                context_size=int(os.environ.get('MODEL_CONTEXT_SIZE', 2048)),
                n_threads=int(os.environ.get('MODEL_THREADS', 4)),
            ),

            rate_limit=RateLimitConfig(
                requests_per_minute=int(os.environ.get('RATE_LIMIT_PER_MINUTE', 30)),
                requests_per_hour=int(os.environ.get('RATE_LIMIT_PER_HOUR', 500)),
            ),

            security=SecurityConfig(
                allowed_origins=allowed_origins,
                max_text_length=int(os.environ.get('MAX_TEXT_LENGTH', 2000)),
                max_symptom_length=int(os.environ.get('MAX_SYMPTOM_LENGTH', 1000)),
                enable_rate_limiting=os.environ.get('ENABLE_RATE_LIMITING', 'true').lower() == 'true',
            ),

            inference=InferenceConfig(
                translation_temperature=float(os.environ.get('TRANSLATION_TEMPERATURE', 0.3)),
                triage_temperature=float(os.environ.get('TRIAGE_TEMPERATURE', 0.2)),
                max_translation_tokens=int(os.environ.get('MAX_TRANSLATION_TOKENS', 256)),
                max_triage_tokens=int(os.environ.get('MAX_TRIAGE_TOKENS', 512)),
            ),
        )


# Medical glossary for accurate translation
MEDICAL_GLOSSARY = """
Twi Medical Terms (use exactly as provided):
- tirim yɛ me ya = headache (head pain)
- me yafun yɛ me ya = stomach pain
- me ho hyehye me = fever (body is hot)
- me bo me fu = nausea
- ahoma/mframa guan = malaria symptoms
- me ani so awu = dizziness/vision problems
- me ho yɛ me yaw = body aches
- me mene ahoma = difficulty breathing
"""


# Singleton config instance
_config: Optional[AppConfig] = None


def get_config() -> AppConfig:
    """Get the application configuration singleton."""
    global _config
    if _config is None:
        _config = AppConfig.from_env()
    return _config


def reload_config() -> AppConfig:
    """Force reload configuration from environment."""
    global _config
    _config = AppConfig.from_env()
    return _config


__all__ = [
    'AppConfig',
    'ModelConfig',
    'RateLimitConfig',
    'SecurityConfig',
    'InferenceConfig',
    'get_config',
    'reload_config',
    'MEDICAL_GLOSSARY',
]
