#!/usr/bin/env bash
# run_real_medgemma_backend_test.sh
# 
# Resolves P1-03: Tests the backend integration using the ACTUAL MedGemma 4B model.
# By default, tests use a mock model to run fast on any machine.
# This script configures the environment to build llama-cpp-python and runs
# the heavy integration tests. Unmocked.

set -e

echo "=== MedGemma Backend Integration Test ==="

if [ ! -d ".audit_venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv .audit_venv
fi

source .audit_venv/bin/activate

echo "Installing base requirements..."
pip install -r cloud/inference_api/requirements.txt
pip install pytest pytest-cov

echo "Installing llama-cpp-python for native execution..."
# If on Mac M-series, use Metal. Otherwise fallback to CMake default.
export CMAKE_ARGS="-DGGML_METAL=on"
export FORCE_CMAKE=1
pip install llama-cpp-python==0.3.7

echo "Running unmocked integration test suite..."
export PYTHONPATH=cloud/inference_api
pytest tests/test_integration.py::TestUnmockedSensorFusionIntegration -v

echo "=== Test Complete ==="
