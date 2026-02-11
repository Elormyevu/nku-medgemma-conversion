from google.cloud import aiplatform

PROJECT_ID = "nku-impact-challenge-1335"
REGION = "us-central1"
# NEW JOB ID (v24 Re-run)
JOB_ID = "8239116904438956032"

def check_status():
    aiplatform.init(project=PROJECT_ID, location=REGION)
    try:
        job = aiplatform.CustomJob.get(resource_name=JOB_ID)
        print(f"📦 Job Status: {job.state}")
        if job.error:
            print(f"❌ Error: {job.error}")
    except Exception as e:
        print(f"⚠️ Error retrieving job: {e}")

if __name__ == "__main__":
    check_status()
