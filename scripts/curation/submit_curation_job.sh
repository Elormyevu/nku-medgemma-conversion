#!/bin/bash

# Configuration
PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
IMAGE_URI="gcr.io/${PROJECT_ID}/nku-curator:latest"
JOB_NAME="nku-curation-$(date +%Y%m%d-%H%M%S)"

GCLOUD="/Users/elormyevudza/google-cloud-sdk/bin/gcloud --project=$PROJECT_ID --account=$ACCOUNT"

echo "------------------------------------------------"
echo "Submitting Dynamic Data Curation Job"
echo "Job Name: $JOB_NAME"
echo "------------------------------------------------"

# 1. Build and Push Curator Image
echo "[1/2] Building and pushing curator image..."
$GCLOUD builds submit scripts/curation/ --tag $IMAGE_URI

# 2. Submit Vertex AI Curation Job
echo "[2/2] Submitting Curation Job to Vertex AI..."
$GCLOUD ai custom-jobs create \
  --region=$REGION \
  --display-name=$JOB_NAME \
  --config=scripts/curation/curation_job_spec.yaml

echo "------------------------------------------------"
echo "Success! Curation Job submitted."
echo "Monitor logs here: https://console.cloud.google.com/vertex-ai/training/jobs?project=$PROJECT_ID"
