import cv2
import numpy as np
import random
import os

class Sim2RealDegrader:
    """
    Applies realistic sensor degradations to synthetic medical images.
    Simulates the optical and processing characteristics of budget Android devices
    (e.g., Tecno Spark, Infinix Hot, Itel) common in West Africa.
    """
    
    def __init__(self):
        pass

    def apply_sensor_noise(self, image, iso_level=800):
        """
        Adds Poisson-Gaussian noise to simulate high ISO granular noise.
        """
        row, col, ch = image.shape
        mean = 0
        # Higher ISO = Higher Variance
        sigma = (iso_level / 100) * 1.5 
        gauss = np.random.normal(mean, sigma, (row, col, ch)).reshape(row, col, ch)
        noisy = image + gauss
        return np.clip(noisy, 0, 255).astype(np.uint8)

    def apply_motion_blur(self, image, kernel_size=5):
        """
        Simulates shaky hands during capture. 
        """
        if kernel_size % 2 == 0: kernel_size += 1
        
        # Create a random direction kernel
        kernel_motion_blur = np.zeros((kernel_size, kernel_size))
        kernel_motion_blur[int((kernel_size-1)/2), :] = np.ones(kernel_size)
        kernel_motion_blur = kernel_motion_blur / kernel_size
        
        # Randomly rotate the kernel
        angle = random.randint(0, 360)
        M = cv2.getRotationMatrix2D((kernel_size/2, kernel_size/2), angle, 1)
        kernel_motion_blur = cv2.warpAffine(kernel_motion_blur, M, (kernel_size, kernel_size))
        
        return cv2.filter2D(image, -1, kernel_motion_blur)

    def apply_cheap_lens_distortion(self, image):
        """
        Simulates slight chromatic aberration and softening at edges.
        """
        # Simple implementation: Shift channels
        b, g, r = cv2.split(image)
        
        rows, cols = image.shape[:2]
        
        # Shift Red channel slightly to left
        M_r = np.float32([[1, 0, 1.5], [0, 1, 0]])
        r_shifted = cv2.warpAffine(r, M_r, (cols, rows))
        
        # Shift Blue channel slightly to right
        M_b = np.float32([[1, 0, -1.5], [0, 1, 0]])
        b_shifted = cv2.warpAffine(b, M_b, (cols, rows))
        
        return cv2.merge([b_shifted, g, r_shifted])

    def apply_aggressive_beautification(self, image):
        """
        Simulates the unwanted 'Beauty Mode' often on by default in Transsion phones.
        This smooths skin texture (removing diagnostic detail) and boosts contrast.
        """
        # 1. Bilateral Filter (Edge-preserving smoothing)
        smoothed = cv2.bilateralFilter(image, 9, 75, 75)
        
        # 2. Boost Contrast/Saturation
        hsv = cv2.cvtColor(smoothed, cv2.COLOR_BGR2HSV)
        hsv[:,:,1] = hsv[:,:,1] * 1.2 # Boost Saturation
        hsv[:,:,2] = hsv[:,:,2] * 1.1 # Boost Brightness (V)
        
        # Clip
        hsv = np.clip(hsv, 0, 255).astype(np.uint8)
        return cv2.cvtColor(hsv, cv2.COLOR_HSV2BGR)

    def process_image(self, image_path, output_path):
        if not os.path.exists(image_path):
            print(f"File not found: {image_path}")
            return

        img = cv2.imread(image_path)
        
        # Pipeline: Clean -> Noisy -> Blurry -> Distorted -> Processed
        
        # 1. Add Hardware Noise (Sensor)
        img = self.apply_sensor_noise(img, iso_level=random.choice([400, 800, 1600]))
        
        # 2. Add Optical Flaws (Lens)
        img = self.apply_cheap_lens_distortion(img)
        
        # 3. Add User Error (Blur)
        if random.random() > 0.5:
            img = self.apply_motion_blur(img, kernel_size=random.choice([3, 5, 7]))
            
        # 4. Add ISP "Damage" (Beautification)
        # This is the hardest part to reverse!
        if random.random() > 0.3:
            img = self.apply_aggressive_beautification(img)
            
        cv2.imwrite(output_path, img)
        print(f"Processed {output_path}")

if __name__ == "__main__":
    # Test
    # degrader = Sim2RealDegrader()
    # degrader.process_image("input.jpg", "output_degraded.jpg")
    pass
