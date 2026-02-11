# =============================================================================
# Nku Inference API — Multi-Stage Docker Build
# Stage 1: Build llama-cpp-python (requires cmake, build-essential)
# Stage 2: Production image (slim, no dev tools)
# =============================================================================

# --- Stage 1: Builder ---
FROM python:3.11-slim AS builder

RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# --- Stage 2: Production ---
FROM python:3.11-slim

# Runtime dependency: llama-cpp-python requires libgomp (OpenMP)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy installed Python packages from builder
COPY --from=builder /install /usr/local

# Security: run as non-root user
RUN useradd --create-home --shell /bin/bash nku
USER nku

WORKDIR /app

# Copy ALL application source files (H-1 fix: was only copying main.py)
COPY --chown=nku:nku main.py .
COPY --chown=nku:nku config.py .
COPY --chown=nku:nku security.py .
COPY --chown=nku:nku logging_config.py .

# Cloud Run sets PORT environment variable
ENV PORT=8080
ENV PYTHONUNBUFFERED=1
ENV LOG_JSON=true

EXPOSE 8080

# Health check for container orchestrators
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')" || exit 1

# Run with gunicorn — single worker for LLM inference (memory-bound)
CMD ["gunicorn", "--bind", "0.0.0.0:8080", "--workers", "1", "--timeout", "300", "--access-logfile", "-", "main:app"]
