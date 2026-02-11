#!/bin/bash
# MedGemma TFLite Conversion - Local Docker Runner
# This script builds and runs the conversion in a Linux container with torch_xla

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
OUTPUT_DIR="${PROJECT_ROOT}/models/tflite"

echo "🏗️  MedGemma TFLite Conversion"
echo "================================"
echo "Script dir: ${SCRIPT_DIR}"
echo "Output dir: ${OUTPUT_DIR}"
echo ""

# Create output directory
mkdir -p "${OUTPUT_DIR}"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop and try again."
    exit 1
fi

echo "📦 Building Docker image (this may take a while on first run)..."
docker build -t medgemma-tflite-converter -f "${SCRIPT_DIR}/Dockerfile.conversion" "${SCRIPT_DIR}"

echo ""
echo "🚀 Starting conversion..."
echo "   This requires ~16GB RAM and may take 30-60 minutes."
echo "   The model will be downloaded from HuggingFace."
echo ""

# Run the conversion with:
# - Output directory mounted to /output
# - HuggingFace cache mounted for persistence
# - 16GB memory limit (adjust if needed)
docker run --rm \
    -v "${OUTPUT_DIR}:/output" \
    -v "${HOME}/.cache/huggingface:/root/.cache/huggingface" \
    --memory=16g \
    --name medgemma-converter \
    medgemma-tflite-converter

# Check if conversion succeeded
if [ -f "${OUTPUT_DIR}/medgemma_int4.tflite" ]; then
    SIZE=$(du -h "${OUTPUT_DIR}/medgemma_int4.tflite" | cut -f1)
    echo ""
    echo "✅ Conversion complete!"
    echo "📁 Output: ${OUTPUT_DIR}/medgemma_int4.tflite"
    echo "📦 Size: ${SIZE}"
else
    echo ""
    echo "❌ Conversion failed - no output file found"
    exit 1
fi
