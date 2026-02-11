from google.cloud import aiplatform

def check_latest_job(project_id, location):
    aiplatform.init(project=project_id, location=location)
    jobs = aiplatform.CustomJob.list(order_by="create_time desc")
    
    if not jobs:
        print("No jobs found.")
        return

    latest_job = jobs[0]
    print(f"--- VERTEX AI JOB PROOF ---")
    print(f"Display Name: {latest_job.display_name}")
    print(f"Job ID: {latest_job.resource_name}")
    print(f"State: {latest_job.state.name}")
    print(f"Creation Time: {latest_job.create_time}")
    print(f"Container: {latest_job.to_dict().get('jobSpec', {}).get('workerPoolSpecs', [{}])[0].get('containerSpec', {}).get('imageUri', 'N/A')}")
    print(f"---------------------------")

if __name__ == "__main__":
    check_latest_job(project_id="nku-impact-challenge-1335", location="us-central1")
