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
import os
import traceback
import threading
import signal

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

# Optional API key validation (S-04)
_api_key = os.environ.get('NKU_API_KEY')

# Lazy-loaded models with thread-safe lock (B-01/B-02)
_model_lock = threading.Lock()
medgemma: Optional[Llama] = None
translategemma: Optional[Llama] = None


# =============================================================================
# SECURITY HEADERS (S-06)
# =============================================================================

@app.after_request
def add_security_headers(response):
    """Add security headers to all responses."""
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['X-Frame-Options'] = 'DENY'
    response.headers['X-XSS-Protection'] = '1; mode=block'
    response.headers['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains'
    response.headers['Content-Security-Policy'] = "default-src 'none'; frame-ancestors 'none'"
    response.headers['Referrer-Policy'] = 'no-referrer'
    return response


def require_api_key(f):
    """Decorator: require NKU_API_KEY header if configured (S-04)."""
    @wraps(f)
    def decorated(*args, **kwargs):
        if _api_key:
            provided = request.headers.get('X-API-Key', '')
            if provided != _api_key:
                return jsonify({
                    'error': 'unauthorized',
                    'message': 'Invalid or missing API key'
                }), 401
        return f(*args, **kwargs)
    return decorated


# =============================================================================
# ERROR HANDLING
# =============================================================================

def _get_request_id() -> str:
    """Get request correlation ID from logging context (B-04)."""
    try:
        from logging_config import request_id_var
        return request_id_var.get() or 'unknown'
    except Exception:
        return 'unknown'


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
    
    # Don't expose internal errors in production (B-04: include request_id)
    rid = _get_request_id()
    if config.debug:
        return jsonify({
            'error': 'internal_error',
            'message': str(e),
            'type': type(e).__name__,
            'request_id': rid
        }), 500
    else:
        return jsonify({
            'error': 'internal_error',
            'message': 'An unexpected error occurred. Please try again later.',
            'request_id': rid
        }), 500


@app.errorhandler(400)
def handle_bad_request(e):
    return jsonify({
        'error': 'bad_request',
        'message': str(e.description) if hasattr(e, 'description') else 'Bad request',
        'request_id': _get_request_id()
    }), 400


@app.errorhandler(429)
def handle_rate_limit(e):
    return jsonify({
        'error': 'rate_limit_exceeded',
        'message': 'Too many requests. Please slow down.',
        'request_id': _get_request_id()
    }), 429


# =============================================================================
# MODEL LOADING
# =============================================================================

class InferenceTimeout(Exception):
    """Raised when LLM inference exceeds timeout (B-06)."""
    pass


def _timeout_handler(signum, frame):
    raise InferenceTimeout("LLM inference timed out")


def with_timeout(timeout_seconds: int = 120):
    """Decorator: abort LLM call if it exceeds timeout_seconds (B-06)."""
    def decorator(f):
        @wraps(f)
        def wrapped(*args, **kwargs):
            # signal.alarm only works on main thread; skip in workers
            try:
                old_handler = signal.signal(signal.SIGALRM, _timeout_handler)
                signal.alarm(timeout_seconds)
                try:
                    return f(*args, **kwargs)
                finally:
                    signal.alarm(0)
                    signal.signal(signal.SIGALRM, old_handler)
            except (ValueError, OSError):
                # Not on main thread — run without timeout
                return f(*args, **kwargs)
        return wrapped
    return decorator


def load_models() -> Tuple[bool, Optional[str]]:
    """
    Download and load models on first request.
    Thread-safe via _model_lock (B-01/B-02).
    Returns (success, error_message).
    """
    global medgemma, translategemma
    
    # Fast path: already loaded
    if medgemma is not None and translategemma is not None:
        return True, None
    
    with _model_lock:
        # Double-check inside lock
        try:
            if medgemma is None:
                request_logger.info("Downloading MedGemma...")
                # B-07: Explicitly pass token — deploy.sh may set HUGGINGFACE_TOKEN
                # but huggingface_hub expects HF_TOKEN. Support both.
                hf_token = os.environ.get('HF_TOKEN') or os.environ.get('HUGGINGFACE_TOKEN')
                med_path = hf_hub_download(
                    config.model.medgemma_repo, 
                    config.model.medgemma_file,
                    token=hf_token
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
                hf_token = os.environ.get('HF_TOKEN') or os.environ.get('HUGGINGFACE_TOKEN')
                trans_path = hf_hub_download(
                    config.model.translategemma_repo, 
                    config.model.translategemma_file,
                    token=hf_token
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
    """Liveness probe — is the process alive? (S-01: version hidden)"""
    return jsonify({
        "status": "ok", 
        "service": "nku-inference"
    })


@app.route('/ready', methods=['GET'])
def ready():
    """Readiness probe — can this instance serve inference requests?"""
    models_ready = medgemma is not None and translategemma is not None
    status_code = 200 if models_ready else 503
    return jsonify({
        "status": "ready" if models_ready else "not_ready",
        "service": "nku-inference",
        "models": {
            "medgemma": medgemma is not None,
            "translategemma": translategemma is not None
        }
    }), status_code


# =============================================================================
# TRANSLATION ENDPOINT
# =============================================================================

@app.route('/translate', methods=['POST'])
@log_request(logger)
@rate_limit(rate_limiter)
@require_api_key
@validate_json_request(required_fields=['text'])
@require_models
@with_timeout(120)
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
    
    # Build safe prompt (B-05: include MEDICAL_GLOSSARY for Twi translations)
    glossary_hint = MEDICAL_GLOSSARY if source_lang == 'twi' or target_lang == 'twi' else ''
    prompt = PromptProtector.build_translation_prompt(
        text_result.sanitized_value,
        source_lang=source_lang,
        target_lang=target_lang,
        glossary=glossary_hint
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
@require_api_key
@validate_json_request(required_fields=['symptoms'])
@require_models
@with_timeout(120)
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
@require_api_key
@validate_json_request(required_fields=['text'])
@require_models
@with_timeout(300)
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
            twi_input, source_lang='twi', target_lang='en',
            glossary=MEDICAL_GLOSSARY
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
            assessment, source_lang='en', target_lang='twi',
            glossary=MEDICAL_GLOSSARY
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
