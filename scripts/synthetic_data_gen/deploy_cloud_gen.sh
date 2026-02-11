#!/bin/bash
set -e

# Fallback: Check standard paths
export PATH=$PATH:/usr/local/bin:/opt/homebrew/bin:$HOME/google-cloud-sdk/bin

# Verify gcloud exists
if ! command -v gcloud &> /dev/null; then
    echo "❌ gcloud command not found. Please ensure Google Cloud SDK is installed."
    exit 1
fi

PROJECT_ID="nku-impact-challenge-1335"
ACCOUNT="wizzyevu@gmail.com"
REGION="us-central1"
REPO="medgemma-repo"
IMAGE_NAME="blender-gen"
TAG="latest"
GCS_BUCKET="gs://nku-impact-data-1335"
IMAGE_URI="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO/$IMAGE_NAME:$TAG"

GCLOUD="gcloud --project=$PROJECT_ID --account=$ACCOUNT"

echo "🔍 Project: $PROJECT_ID | Account: $ACCOUNT"
echo "🔌 Enabling required APIs..."
$GCLOUD services enable cloudbuild.googleapis.com artifactregistry.googleapis.com run.googleapis.com

echo "📦 Checking Artifact Registry..."
$GCLOUD artifacts repositories create $REPO \
    --repository-format=docker \
    --location=$REGION \
    --description="MedGemma Docker Repository" \
    || echo "Repository likely exists, proceeding..."

echo "☁️  Building in Cloud (Cloud Build)..."
$GCLOUD builds submit --config scripts/synthetic_data_gen/cloudbuild.yaml --substitutions=_IMAGE_URI=$IMAGE_URI .

echo "🚀 Submitting Cloud Run Job (High Performance Mode)..."
# Maximize resources for "No Shortcuts" performance
$GCLOUD run jobs create blender-gen-job \
  --image $IMAGE_URI \
  --region $REGION \
  --set-env-vars GCS_BUCKET=$GCS_BUCKET,NUM_SAMPLES=1000 \
  --memory 32Gi \
  --cpu 8 \
  --execute-now

echo "✅ Job submitted! Logs available in Cloud Console."
