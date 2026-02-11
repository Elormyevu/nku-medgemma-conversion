import cv2
import numpy as np

class ColorCalibrator:
    """
    Implements the 'White Paper' calibration hack for sensorless diagnostics.
    Corrects for ambient lighting color cast (Kelvin) and exposure intensity.
    """
    
    def __init__(self, target_white_val=240):
        # We target 240, not 255, to avoid clipping highlights which destroys data
        self.target_white = target_white_val

    def get_white_balance_gains(self, image, roi_tuple):
        """
        Calculates the RGB gains needed to make the ROI neutral white/grey.
        roi_tuple: (x, y, w, h)
        """
        x, y, w, h = roi_tuple
        
        # Extract ROI
        roi = image[y:y+h, x:x+w]
        
        # Calculate mean of each channel in ROI
        # OpenCV uses BGR
        avg_b = np.mean(roi[:, :, 0])
        avg_g = np.mean(roi[:, :, 1])
        avg_r = np.mean(roi[:, :, 2])
        
        # Prevent divide by zero
        avg_b = max(avg_b, 1.0)
        avg_g = max(avg_g, 1.0)
        avg_r = max(avg_r, 1.0)

        # Calculate Gains to match Target White (Von Kries adaptation)
        gain_b = self.target_white / avg_b
        gain_g = self.target_white / avg_g
        gain_r = self.target_white / avg_r
        
        return (gain_b, gain_g, gain_r)

    def apply_calibration(self, image, gains):
        """
        Applies gains to the entire image.
        """
        gain_b, gain_g, gain_r = gains
        
        # Multiply channels by gains
        # Split
        b, g, r = cv2.split(image)
        
        # Multiply (use float to prevent overflow during calc)
        b = cv2.multiply(b.astype(np.float32), gain_b)
        g = cv2.multiply(g.astype(np.float32), gain_g)
        r = cv2.multiply(r.astype(np.float32), gain_r)
        
        # Clip to 255 and cast back to uint8
        b = np.clip(b, 0, 255).astype(np.uint8)
        g = np.clip(g, 0, 255).astype(np.uint8)
        r = np.clip(r, 0, 255).astype(np.uint8)
        
        return cv2.merge([b, g, r])

    def run_pipeline(self, image_path, roi_tuple, output_path):
        """
        Full pipeline: Load -> Measure -> Correct -> Save
        """
        img = cv2.imread(image_path)
        if img is None:
            print(f"Error: Could not read {image_path}")
            return

        print(f"Calibrating {image_path} using ROI {roi_tuple}...")
        
        gains = self.get_white_balance_gains(img, roi_tuple)
        print(f"Calculated Gains - B: {gains[0]:.2f}, G: {gains[1]:.2f}, R: {gains[2]:.2f}")
        
        corrected = self.apply_calibration(img, gains)
        
        cv2.imwrite(output_path, corrected)
        print(f"Saved calibrated image to {output_path}")

if __name__ == "__main__":
    # Example usage
    # calibrator = ColorCalibrator()
    # Assume we have a synthetic image where x=0, y=0, w=50, h=50 is the 'white paper'
    # calibrator.run_pipeline("synthetic_test.jpg", (0, 0, 50, 50), "calibrated.jpg")
    pass
