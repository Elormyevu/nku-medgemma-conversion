import requests
import os

# 1. Fetch current HF README to preserve YAML frontmatter and MedQA Table
url = "https://huggingface.co/wredd/medgemma-4b-gguf/raw/main/README.md"
r = requests.get(url)
hf_content = r.text

# Split at "### Project name" (where the Kaggle writeup begins)
header_part = hf_content.split("### Project name")[0]

# 2. Read the local updated writeup with the 32 citations
with open("kaggle_submission_writeup.md", "r") as f:
    writeup_content = f.read()

# 3. Merge them
final_content = header_part + writeup_content

with open("FINAL_HF_README_PAYLOAD.md", "w") as f:
    f.write(final_content)
    
print("Successfully prepared FINAL_HF_README_PAYLOAD.md")
