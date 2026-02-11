#!/bin/bash
# Script to download HeAR model weights from Hugging Face
# Requires git-lfs
echo "Installing git-lfs..."
git lfs install
echo "Cloning HeAR model from Hugging Face..."
# Note: This is an example URL based on typical HF patterns. 
# The actual repo is google/hear.
git clone https://huggingface.co/google/hear models/hear
echo "Download complete."
