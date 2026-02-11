FROM pytorch/pytorch:2.4.0-cuda12.4-cudnn9-runtime

WORKDIR /app

RUN pip install --no-cache-dir \
    sentence-transformers \
    scikit-learn \
    tqdm \
    google-cloud-storage \
    numpy

COPY dynamic_curator.py .

ENTRYPOINT ["python", "dynamic_curator.py"]
