"""
Nku Logging Configuration
Provides structured JSON logging with request correlation IDs.
"""

import logging
import json
import sys
import uuid
import time
from datetime import datetime
from typing import Optional, Dict, Any
from functools import wraps
from contextvars import ContextVar

# Context variable for request correlation
request_id_var: ContextVar[Optional[str]] = ContextVar('request_id', default=None)


class JSONFormatter(logging.Formatter):
    """Custom JSON formatter for structured logging."""
    
    def format(self, record: logging.LogRecord) -> str:
        log_data = {
            'timestamp': datetime.utcnow().isoformat() + 'Z',
            'level': record.levelname,
            'logger': record.name,
            'message': record.getMessage(),
            'module': record.module,
            'function': record.funcName,
            'line': record.lineno,
        }
        
        # Add request ID if available
        request_id = request_id_var.get()
        if request_id:
            log_data['request_id'] = request_id
        
        # Add exception info if present (H-4 fix: full stack traces)
        if record.exc_info and record.exc_info[0] is not None:
            log_data['exception'] = {
                'type': record.exc_info[0].__name__,
                'message': str(record.exc_info[1]),
                'traceback': self.formatException(record.exc_info),
            }
        
        # Add stack info if present
        if record.stack_info:
            log_data['stack_info'] = self.formatStack(record.stack_info)
        
        # Add extra fields
        if hasattr(record, 'extra_data'):
            log_data.update(record.extra_data)
        
        return json.dumps(log_data)


class RequestLogger:
    """Logger adapter for request-scoped logging."""
    
    def __init__(self, logger: logging.Logger):
        self.logger = logger
    
    def _log(self, level: int, message: str, extra_data: Dict[str, Any] = None, exc_info=None):
        record = self.logger.makeRecord(
            self.logger.name, level, '', 0, message, (), exc_info
        )
        if extra_data:
            record.extra_data = extra_data
        self.logger.handle(record)
    
    def info(self, message: str, **kwargs):
        self._log(logging.INFO, message, kwargs if kwargs else None)
    
    def warning(self, message: str, **kwargs):
        self._log(logging.WARNING, message, kwargs if kwargs else None)
    
    def error(self, message: str, exc_info=None, **kwargs):
        self._log(logging.ERROR, message, kwargs if kwargs else None, exc_info=exc_info)
    
    def debug(self, message: str, **kwargs):
        self._log(logging.DEBUG, message, kwargs if kwargs else None)


def setup_logging(level: str = 'INFO', json_format: bool = True) -> logging.Logger:
    """Configure application logging."""
    
    log_level = getattr(logging, level.upper(), logging.INFO)
    
    # Create root logger
    logger = logging.getLogger('nku')
    logger.setLevel(log_level)
    
    # Remove existing handlers
    logger.handlers.clear()
    
    # Create console handler
    handler = logging.StreamHandler(sys.stdout)
    handler.setLevel(log_level)
    
    if json_format:
        handler.setFormatter(JSONFormatter())
    else:
        handler.setFormatter(logging.Formatter(
            '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
        ))
    
    logger.addHandler(handler)
    
    return logger


def log_request(logger: logging.Logger):
    """Decorator to log Flask request/response."""
    def decorator(f):
        @wraps(f)
        def decorated_function(*args, **kwargs):
            from flask import request, g
            
            # Generate request ID
            request_id = str(uuid.uuid4())[:8]
            request_id_var.set(request_id)
            g.request_id = request_id
            g.request_start = time.time()
            
            # Log request
            logger.info(f"Request started: {request.method} {request.path}", extra={
                'extra_data': {
                    'method': request.method,
                    'path': request.path,
                    'client_ip': request.remote_addr,
                    'user_agent': request.headers.get('User-Agent', '')[:100]
                }
            })
            
            try:
                response = f(*args, **kwargs)
                
                # Log response
                duration_ms = (time.time() - g.request_start) * 1000
                status_code = response.status_code if hasattr(response, 'status_code') else 200
                
                logger.info(f"Request completed: {status_code}", extra={
                    'extra_data': {
                        'status_code': status_code,
                        'duration_ms': round(duration_ms, 2)
                    }
                })
                
                return response
                
            except Exception as e:
                import traceback
                duration_ms = (time.time() - g.request_start) * 1000
                logger.error(f"Request failed: {str(e)}", exc_info=True, extra={
                    'extra_data': {
                        'error': str(e),
                        'error_type': type(e).__name__,
                        'traceback': traceback.format_exc(),
                        'duration_ms': round(duration_ms, 2)
                    }
                })
                raise
        
        return decorated_function
    return decorator


def get_logger(name: str = 'nku') -> RequestLogger:
    """Get a request-aware logger."""
    return RequestLogger(logging.getLogger(name))


__all__ = [
    'setup_logging',
    'log_request',
    'get_logger',
    'request_id_var',
]
