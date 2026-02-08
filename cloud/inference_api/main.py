"""
Nku Cloud Inference API
Offloads LLM processing to cloud for low-memory devices.
Deploy to Google Cloud Run.

SECURITY NOTES:
- All endpoints require authentication (removed --allow-unauthenticated)
- Input validation on all user inputs
- Prompt injection protection on all LLM calls
- Rate limiting enabled by default
- Structured logging for observability
"""

from flask import Flask, request, jsonify, g
from functools import wraps
from typing import Optional, Tuple, Dict, Any
import traceback

from huggingface_hub import hf_hub_download

try:
    from llama_cpp import Llama
except ImportError:
    Llama = None  # Allow running tests without native library

# Local modules — support both direct execution and package import
try:
    from config import get_config, MEDICAL_GLOSSARY
    from security import (
        InputValidator, 
        PromptProtector, 
        RateLimiter, 
        rate_limit,
        configure_cors,
        validate_json_request
    )
    from logging_config import setup_logging, log_request, get_logger
except ImportError:
    from .config import get_config, MEDICAL_GLOSSARY
    from .security import (
        InputValidator, 
        PromptProtector, 
        RateLimiter, 
        rate_limit,
        configure_cors,
        validate_json_request
    )
    from .logging_config import setup_logging, log_request, get_logger


# =============================================================================
# APPLICATION SETUP
# =============================================================================

app = Flask(__name__)
config = get_config()

# Setup logging
logger = setup_logging(level=config.log_level, json_format=config.log_json)
request_logger = get_logger('nku.api')

# Configure CORS (secure by default)
configure_cors(app, allowed_origins=config.security.allowed_origins)

# Initialize security components
input_validator = InputValidator()
rate_limiter = RateLimiter(
    requests_per_minute=config.rate_limit.requests_per_minute,
    requests_per_hour=config.rate_limit.requests_per_hour
)

# Lazy-loaded models
medgemma: Optional[Llama] = None
translategemma: Optional[Llama] = None


# =============================================================================
# ERROR HANDLING
# =============================================================================

@app.errorhandler(Exception)
def handle_exception(e):
    """Global exception handler."""
    # Let Flask handle HTTP exceptions (404, 405, etc.) natively
    from werkzeug.exceptions import HTTPException
    if isinstance(e, HTTPException):
        return e
    
    request_logger.error(f"Unhandled exception: {str(e)}", 
                         error_type=type(e).__name__,
                         traceback=traceback.format_exc())
    
    # Don't expose internal errors in production
    if config.debug:
        return jsonify({
            'error': 'internal_error',
            'message': str(e),
            'type': type(e).__name__
        }), 500
    else:
        return jsonify({
            'error': 'internal_error',
            'message': 'An unexpected error occurred. Please try again later.'
        }), 500


@app.errorhandler(400)
def handle_bad_request(e):
    return jsonify({
        'error': 'bad_request',
        'message': str(e.description) if hasattr(e, 'description') else 'Bad request'
    }), 400


@app.errorhandler(429)
def handle_rate_limit(e):
    return jsonify({
        'error': 'rate_limit_exceeded',
        'message': 'Too many requests. Please slow down.'
    }), 429


# =============================================================================
# MODEL LOADING
# =============================================================================

def load_models() -> Tuple[bool, Optional[str]]:
    """
    Download and load models on first request.
    Returns (success, error_message).
    """
    global medgemma, translategemma
    
    try:
        if medgemma is None:
            request_logger.info("Downloading MedGemma...")
            med_path = hf_hub_download(
                config.model.medgemma_repo, 
                config.model.medgemma_file
            )
            medgemma = Llama(
                model_path=med_path,
                n_ctx=config.model.context_size,
                n_gpu_layers=config.model.n_gpu_layers,
                n_threads=config.model.n_threads,
                verbose=False
            )
            request_logger.info("MedGemma loaded successfully")
        
        if translategemma is None:
            request_logger.info("Downloading TranslateGemma...")
            trans_path = hf_hub_download(
                config.model.translategemma_repo, 
                config.model.translategemma_file
            )
            translategemma = Llama(
                model_path=trans_path,
                n_ctx=config.model.context_size,
                n_gpu_layers=config.model.n_gpu_layers,
                n_threads=config.model.n_threads,
                verbose=False
            )
            request_logger.info("TranslateGemma loaded successfully")
        
        return True, None
        
    except Exception as e:
        error_msg = f"Failed to load models: {str(e)}"
        request_logger.error(error_msg, error_type=type(e).__name__)
        return False, error_msg


def require_models(f):
    """Decorator to ensure models are loaded before endpoint execution."""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        success, error = load_models()
        if not success:
            return jsonify({
                'error': 'model_load_failed',
                'message': 'Failed to load AI models. Please try again later.'
            }), 503
        return f(*args, **kwargs)
    return decorated_function


# =============================================================================
# HEALTH CHECK
# =============================================================================

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint."""
    return jsonify({
        "status": "ok", 
        "service": "nku-inference",
        "version": "2.0.0",
        "models_loaded": medgemma is not None and translategemma is not None
    })


# =============================================================================
# TRANSLATION ENDPOINT
# =============================================================================

@app.route('/translate', methods=['POST'])
@log_request(logger)
@rate_limit(rate_limiter)
@validate_json_request(required_fields=['text'])
@require_models
def translate():
    """
    Translate between Twi and English.
    
    Request body:
        - text (required): Text to translate
        - source (optional): Source language code (default: 'twi')
        - target (optional): Target language code (default: 'en')
    """
    data = request.get_json()
    
    # Validate text input
    text_result = input_validator.validate_text(
        data.get('text'), 
        max_length=config.security.max_text_length
    )
    if not text_result.is_valid:
        return jsonify({
            'error': 'validation_error',
            'message': text_result.errors[0]
        }), 400
    
    # Validate language codes
    source_lang = data.get('source', 'twi')
    target_lang = data.get('target', 'en')
    
    source_result = input_validator.validate_language(source_lang)
    if not source_result.is_valid:
        source_lang = 'twi'  # Default to Twi if invalid
    else:
        source_lang = source_result.sanitized_value
    
    target_result = input_validator.validate_language(target_lang)
    if not target_result.is_valid:
        target_lang = 'en'  # Default to English if invalid
    else:
        target_lang = target_result.sanitized_value
    
    # Build safe prompt
    prompt = PromptProtector.build_translation_prompt(
        text_result.sanitized_value,
        source_lang=source_lang,
        target_lang=target_lang
    )
    
    try:
        result = translategemma(
            prompt,
            max_tokens=config.inference.max_translation_tokens,
            temperature=config.inference.translation_temperature,
            stop=["\n\n", "<<<USER_INPUT>>>"]
        )
        
        raw_translation = result['choices'][0]['text'].strip()
        is_valid, translation = PromptProtector.validate_output(raw_translation)
        
        if not is_valid:
            request_logger.warning("Translation output validation failed")
            return jsonify({
                'error': 'generation_error',
                'message': 'Failed to generate valid translation'
            }), 500
        
        return jsonify({
            "translation": translation,
            "source_lang": source_lang,
            "target_lang": target_lang,
            "warnings": text_result.warnings if text_result.warnings else None
        })
        
    except Exception as e:
        request_logger.error(f"Translation inference failed: {str(e)}")
        return jsonify({
            'error': 'inference_error',
            'message': 'Translation failed. Please try again.'
        }), 500


# =============================================================================
# TRIAGE ENDPOINT
# =============================================================================

@app.route('/triage', methods=['POST'])
@log_request(logger)
@rate_limit(rate_limiter)
@validate_json_request(required_fields=['symptoms'])
@require_models
def triage():
    """
    Medical triage analysis.
    
    Request body:
        - symptoms (required): Patient symptoms description
    """
    data = request.get_json()
    
    # Validate symptom input
    symptoms_result = input_validator.validate_symptoms(data.get('symptoms'))
    if not symptoms_result.is_valid:
        return jsonify({
            'error': 'validation_error',
            'message': symptoms_result.errors[0]
        }), 400
    
    # Build safe prompt
    prompt = PromptProtector.build_triage_prompt(symptoms_result.sanitized_value)
    
    try:
        result = medgemma(
            prompt,
            max_tokens=config.inference.max_triage_tokens,
            temperature=config.inference.triage_temperature,
            stop=["<<<USER_INPUT>>>", "Patient symptoms:"]
        )
        
        raw_assessment = result['choices'][0]['text'].strip()
        is_valid, assessment = PromptProtector.validate_output(raw_assessment)
        
        if not is_valid:
            request_logger.warning("Triage output validation failed")
            return jsonify({
                'error': 'generation_error',
                'message': 'Failed to generate valid assessment'
            }), 500
        
        return jsonify({
            "assessment": assessment,
            "warnings": symptoms_result.warnings if symptoms_result.warnings else None
        })
        
    except Exception as e:
        request_logger.error(f"Triage inference failed: {str(e)}")
        return jsonify({
            'error': 'inference_error',
            'message': 'Triage analysis failed. Please try again.'
        }), 500


# =============================================================================
# NKU CYCLE ENDPOINT
# =============================================================================

@app.route('/nku-cycle', methods=['POST'])
@log_request(logger)
@rate_limit(rate_limiter)
@validate_json_request(required_fields=['text'])
@require_models
def nku_cycle():
    """
    Full Nku Cycle: Twi → English → Triage → Twi.
    
    Request body:
        - text (required): Patient symptoms in Twi
    """
    data = request.get_json()
    
    # Validate input
    text_result = input_validator.validate_text(
        data.get('text'),
        max_length=config.security.max_symptom_length
    )
    if not text_result.is_valid:
        return jsonify({
            'error': 'validation_error',
            'message': text_result.errors[0]
        }), 400
    
    twi_input = text_result.sanitized_value
    
    try:
        # Step 1: Translate Twi to English
        trans_prompt = PromptProtector.build_translation_prompt(
            twi_input, source_lang='twi', target_lang='en'
        )
        trans_result = translategemma(
            trans_prompt, 
            max_tokens=config.inference.max_translation_tokens,
            temperature=config.inference.translation_temperature,
            stop=["\n\n", "<<<USER_INPUT>>>"]
        )
        _, english = PromptProtector.validate_output(
            trans_result['choices'][0]['text'].strip()
        )
        
        # Step 2: Medical triage
        triage_prompt = PromptProtector.build_triage_prompt(english)
        triage_result = medgemma(
            triage_prompt,
            max_tokens=config.inference.max_triage_tokens,
            temperature=config.inference.triage_temperature,
            stop=["<<<USER_INPUT>>>"]
        )
        _, assessment = PromptProtector.validate_output(
            triage_result['choices'][0]['text'].strip()
        )
        
        # Step 3: Translate response back to Twi
        back_prompt = PromptProtector.build_translation_prompt(
            assessment, source_lang='en', target_lang='twi'
        )
        back_result = translategemma(
            back_prompt,
            max_tokens=config.inference.max_translation_tokens,
            temperature=config.inference.translation_temperature,
            stop=["\n\n", "<<<USER_INPUT>>>"]
        )
        _, twi_output = PromptProtector.validate_output(
            back_result['choices'][0]['text'].strip()
        )
        
        return jsonify({
            "english_translation": english,
            "triage_assessment": assessment,
            "twi_output": twi_output,
            "warnings": text_result.warnings if text_result.warnings else None
        })
        
    except Exception as e:
        request_logger.error(f"Nku cycle failed: {str(e)}")
        return jsonify({
            'error': 'inference_error',
            'message': 'Processing failed. Please try again.'
        }), 500


# =============================================================================
# MAIN ENTRY POINT
# =============================================================================

if __name__ == '__main__':
    request_logger.info(f"Starting Nku Inference API on port {config.port}")
    app.run(host='0.0.0.0', port=config.port, debug=config.debug)
