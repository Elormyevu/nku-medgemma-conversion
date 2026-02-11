# Base image: PyTorch 2.4 with CUDA 12.4 for A100 optimization
FROM pytorch/pytorch:2.4.0-cuda12.4-cudnn9-runtime

# Set working directory
WORKDIR /app

# Install PyTorch Nightly ONLY (Required for Gemma 3)
# Exclude torchvision/torchaudio to prevent binary incompatibility with transformers
RUN pip uninstall -y torchvision torchaudio || true
RUN pip install --pre torch --index-url https://download.pytorch.org/whl/nightly/cu124 --upgrade

# Install system dependencies
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y \
    git \
    build-essential \
    python3-dev \
    libgl1-mesa-glx \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies for Efficient Fine-tuning (LoRA)
# We use 'transformers', 'peft', 'accelerate', 'bitsandbytes' for QLoRA
# 'google-cloud-storage' for data access
# Force uninstall potentially pre-installed outdated transformers
RUN pip uninstall -y transformers || true

# Install bleeding edge transformers, peft, and accelerate for Gemma 3 support
RUN pip install git+https://github.com/huggingface/transformers.git
RUN pip install git+https://github.com/huggingface/peft.git
RUN pip install git+https://github.com/huggingface/accelerate.git
RUN pip install --no-cache-dir \
    bitsandbytes>=0.43.1 \
    datasets \
    scipy \
    google-cloud-storage \
    gcsfs \
    pillow \
    sentencepiece \
    protobuf

# Permission Guard: Ensure directories are writable for Sentinel Checkpointing
RUN mkdir -p /app/data /app/checkpoints && chmod 777 /app/data /app/checkpoints

# Copy all scripts from the context (scripts/training/)
COPY . .

# No hardcoded ENTRYPOINT/CMD to allow Vertex AI 'command' to work
# But we can provide a default for safety
CMD ["python3", "cloud_train.py"]
