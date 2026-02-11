import cv2
import time
import numpy as np
from roi_extractor import ROIExtractor
from ar_overlay_sim import AROverlaySimulator
from white_paper_calibrator import ColorCalibrator

def main():
    print("--- Nku Desktop Demo Mode ---")
    print("Press 'q' to quit.")
    print("Press 'c' to trigger calibration (hold white paper in blue box).")
    print("--------------------------------")

    # 1. Initialize Components
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("Error: Could not open webcam.")
        return

    # Set High Resolution for demo quality
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

    roi_extractor = ROIExtractor()
    ar_sim = AROverlaySimulator()
    calibrator = ColorCalibrator(target_white_val=240)

    # State
    is_calibrated = False
    calibration_gains = (1.0, 1.0, 1.0)
    
    # Calibration ROI (Fixed box in bottom left)
    # x, y, w, h
    calib_box = (50, 600, 100, 100) 

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        # Mirror view for UX
        frame = cv2.flip(frame, 1)
        h, w = frame.shape[:2]

        # Apply Calibration if active
        if is_calibrated:
            frame = calibrator.apply_calibration(frame, calibration_gains)
            cv2.putText(frame, "CALIBRATED", (w-200, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
        else:
            cv2.putText(frame, "UNCALIBRATED", (w-250, 40), cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 165, 255), 2)

        # 2. ROI Extraction & AR Overlay
        # (Pass RGB to MediaPipe, but draw on BGR)
        # Note: roi_extractor internals handle BGR->RGB conversion
        
        # We need the full landmarks to draw the mesh
        # Simulating logic: Extract eyes -> Check quality -> Draw AR
        left_eye, right_eye = roi_extractor.extract_eye_regions(frame)
        
        # Access internal FaceMesh results for drawing full HUD
        # (In a real app, this would be cleaner, but accessing private attr for demo speed)
        if roi_extractor.face_mesh:
             # Reprocess for visualization (inefficient but fine for desktop demo)
             results = roi_extractor.face_mesh.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
             
             if results.multi_face_landmarks:
                 for face_landmarks in results.multi_face_landmarks:
                     # Calculate alignment (mock logic: check redness or just roll)
                     # For demo, we assume "Aligned" if face detected
                     is_aligned = True 
                     
                     frame = ar_sim.draw_guidance(frame, face_landmarks, is_aligned)
                     
                     # Simulate "Scanning" analysis
                     if is_calibrated:
                         frame = ar_sim.simulate_stroke_visualization(frame, face_landmarks)

        # 3. Draw Calibration Target Box
        cx, cy, cw, ch = calib_box
        color = (255, 0, 0) # Blue
        cv2.rectangle(frame, (cx, cy), (cx+cw, cy+ch), color, 2)
        cv2.putText(frame, "Ref Card", (cx, cy-10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)

        # Display
        cv2.imshow('Nku Sensorless Diagnostics (Demo)', frame)

        # Input Handling
        key = cv2.waitKey(1) & 0xFF
        if key == ord('q'):
            break
        elif key == ord('c'):
            # Trigger Calibration
            print("Calibrating...")
            # Ensure ROI is within bounds
            safe_roi = frame[cy:cy+ch, cx:cx+cw]
            if safe_roi.size > 0:
                calibration_gains = calibrator.get_white_balance_gains(frame, calib_box)
                is_calibrated = True
                print(f"Calibrated Gains: {calibration_gains}")

    # Cleanup
    cap.release()
    cv2.destroyAllWindows()
    roi_extractor.close()

if __name__ == "__main__":
    main()
