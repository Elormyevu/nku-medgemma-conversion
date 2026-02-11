import mlx.core as mx
from mlx_lm import load, generate
import time

def benchmark_inference(model_path, prompt="Describe the symptoms of pediatric malaria."):
    print(f"🚀 Benchmarking inference for model at: {model_path}")
    
    # Load the model
    model, tokenizer = load(model_path)
    
    # Warm up
    print("Warming up...")
    generate(model, tokenizer, prompt=prompt, max_tokens=10)
    
    # Actual benchmark - measure latency and tokens per second
    print("Running benchmark...")
    start_time = time.time()
    response = generate(model, tokenizer, prompt=prompt, max_tokens=100)
    end_time = time.time()
    
    duration = end_time - start_time
    # Note: MLX generation output usually includes token counts in a more complex way if using high-level API, 
    # but we can estimate based on word count / character count if needed, 
    # or just record the total wall-clock time for a standard diagnostic response.
    
    print(f"\n--- INFERENCE BENCHMARK ---")
    print(f"Total Time: {duration:.2f} seconds")
    print(f"Target: <15s for end-to-end diagnosis")
    print(f"Response Preview: {response[:100]}...")
    print(f"---------------------------")

if __name__ == "__main__":
    benchmark_inference("/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/nku_mlx_optimized")
