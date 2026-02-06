#!/bin/bash
# Deploy Nku Inference API to Google Cloud Run
# Requires: gcloud CLI configured with project access
#
# SECURITY: This script deploys WITH authentication enabled.
# Use --service-account or IAM to grant access to authorized clients.

set -e

PROJECT_ID="${GCP_PROJECT_ID:-nku-health}"
REGION="${CLOUD_RUN_REGION:-us-central1}"
SERVICE_NAME="nku-inference"
SERVICE_ACCOUNT="${SERVICE_ACCOUNT:-}"

echo "🚀 Deploying Nku Inference API to Cloud Run..."
echo "   Project: $PROJECT_ID"
echo "   Region: $REGION"
echo "   Service: $SERVICE_NAME"
echo ""
echo "⚠️  SECURITY: Authentication is ENABLED (no --allow-unauthenticated)"
echo ""

cd "$(dirname "$0")"

# Validate HF_TOKEN is set (required for model download)
if [ -z "${HF_TOKEN}" ]; then
    echo "❌ ERROR: HF_TOKEN environment variable is not set"
    echo "   Set it with: export HF_TOKEN=your_token"
    echo "   Or use Secret Manager: gcloud secrets versions access latest --secret=hf-token"
    exit 1
fi

# Build deployment command
DEPLOY_CMD="gcloud run deploy $SERVICE_NAME \
    --project=$PROJECT_ID \
    --region=$REGION \
    --source=. \
    --memory=8Gi \
    --cpu=4 \
    --timeout=300s \
    --max-instances=3 \
    --min-instances=0 \
    --concurrency=1 \
    --set-env-vars=LOG_LEVEL=INFO,LOG_JSON=true"

# Add service account if specified
if [ -n "$SERVICE_ACCOUNT" ]; then
    DEPLOY_CMD="$DEPLOY_CMD --service-account=$SERVICE_ACCOUNT"
fi

# Use Secret Manager for HF_TOKEN if available, otherwise use env var
if gcloud secrets describe hf-token --project="$PROJECT_ID" &>/dev/null; then
    echo "📦 Using Secret Manager for HF_TOKEN"
    DEPLOY_CMD="$DEPLOY_CMD --set-secrets=HUGGINGFACE_TOKEN=hf-token:latest"
else
    echo "📦 Using environment variable for HF_TOKEN"
    DEPLOY_CMD="$DEPLOY_CMD --set-env-vars=HUGGINGFACE_TOKEN=${HF_TOKEN}"
fi

# Execute deployment
echo ""
echo "🔧 Running deployment..."
eval $DEPLOY_CMD

echo ""
echo "✅ Deployment complete!"
echo ""
echo "📋 Next steps:"
echo "   1. Get the service URL:"
echo "      gcloud run services describe $SERVICE_NAME --region=$REGION --format='value(status.url)'"
echo ""
echo "   2. Grant access to authorized users/service accounts:"
echo "      gcloud run services add-iam-policy-binding $SERVICE_NAME \\"
echo "        --region=$REGION \\"
echo "        --member='user:email@example.com' \\"
echo "        --role='roles/run.invoker'"
echo ""
echo "   3. For mobile app access, create a service account and grant invoker role:"
echo "      gcloud iam service-accounts create nku-mobile-client"
echo "      gcloud run services add-iam-policy-binding $SERVICE_NAME \\"
echo "        --region=$REGION \\"
echo "        --member='serviceAccount:nku-mobile-client@$PROJECT_ID.iam.gserviceaccount.com' \\"
echo "        --role='roles/run.invoker'"
echo ""
echo "   4. Update CloudInferenceClient.kt with the URL and auth config"
echo "   5. Rebuild Android app"
