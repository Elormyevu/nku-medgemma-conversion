#!/bin/bash
exec > /Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My\ Drive/0AntigravityProjects/nku-impact-challenge-1335/pytest_output.log 2>&1
source .audit_venv/bin/activate || echo "No venv found"
pytest -q -rs
echo "Done security 1"

export PYTHONPATH=cloud/inference_api
pytest -q cloud/inference_api/test_security_suite.py
echo "Done security 2"
