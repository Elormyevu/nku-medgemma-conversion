#!/bin/bash
set -e

# Default Buckets (can be overridden by env vars)
GCS_BUCKET="${GCS_BUCKET:-gs://nku-impact-data-1335}"
NUM_SAMPLES="${NUM_SAMPLES:-10}"

echo "🚀 Starting Cloud Blender Generation..."
echo "Target Bucket: $GCS_BUCKET"
echo "Samples: $NUM_SAMPLES"

# Create output directory
mkdir -p output

# Run Blender Headless
# -b: background (headless)
# -P: run python script
echo "🎨 Rendering faces..."
blender -b -P generate_faces.py -- --output_dir "/app/output" --num_samples $NUM_SAMPLES

# Write uploader script using Blender's Python
cat <<EOF > upload_to_gcs.py
import os
import sys
from google.cloud import storage

bucket_name = "${GCS_BUCKET}".replace("gs://", "")
source_folder = "/app/output"

print(f"Uploading from {source_folder} to {bucket_name}...")

try:
    storage_client = storage.Client()
    bucket = storage_client.bucket(bucket_name)

    for root, dirs, files in os.walk(source_folder):
        for file in files:
            local_path = os.path.join(root, file)
            blob_path = os.path.relpath(local_path, source_folder)
            blob = bucket.blob(blob_path)
            blob.upload_from_filename(local_path)
            print(f"Uploaded {blob_path}")

except Exception as e:
    print(f"Upload failed: {e}")
    sys.exit(1)
EOF

# Run the uploader using Blender's python (which has the lib installed)
/usr/local/blender/3.6/python/bin/python3.10 upload_to_gcs.py

echo "✅ Generation Complete."
