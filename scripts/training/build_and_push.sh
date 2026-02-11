#!/bin/bash
# Builds and pushes the nku-training Docker image with the correct context

set -e

PROJECT_ID="dora-gemini-research"
IMAGE_NAME="nku-training"
TAG="latest"
IMAGE_URI="gcr.io/${PROJECT_ID}/${IMAGE_NAME}:${TAG}"

echo "🚀 Building and Pushing Docker Image: ${IMAGE_URI}"
echo "📂 Context: scripts/training/"

# Ensure we are in the scripts/training directory
cd "$(dirname "$0")"

# Build using Google Cloud Build
# We submit the current directory (scripts/training) as the build context
/Users/elormyevudza/google-cloud-sdk/bin/gcloud builds submit --tag "${IMAGE_URI}" .

echo "✅ Build and Push Complete."
