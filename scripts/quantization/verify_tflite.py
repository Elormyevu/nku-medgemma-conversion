import numpy as np
import tensorflow as tf
import os

def verify_tflite(path):
    if not os.path.exists(path):
        print(f"Error: {path} not found.")
        return
    
    print(f"Verifying {path} ({os.path.getsize(path)/1e9:.2f}GB)...")
    
    # Load interpreter
    interpreter = tf.lite.Interpreter(model_path=path)
    interpreter.allocate_tensors()
    
    # Get input/output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    # Prepare dummy input
    input_shape = input_details[0]['shape']
    dummy_input = np.zeros(input_shape, dtype=input_details[0]['dtype'])
    
    # Set tensor
    interpreter.set_tensor(input_details[0]['index'], dummy_input)
    
    # Invoke
    print("Running inference...")
    interpreter.invoke()
    
    # Get output
    output_data = interpreter.get_tensor(output_details[0]['index'])
    print(f"Inference SUCCESS. Output shape: {output_data.shape}")
    print(f"Output mean: {np.mean(output_data)}")

if __name__ == "__main__":
    import sys
    path = sys.argv[1] if len(sys.argv) > 1 else "medgemma_int8.tflite"
    verify_tflite(path)
