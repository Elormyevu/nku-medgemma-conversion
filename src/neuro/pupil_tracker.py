import mediapipe as mp
import cv2
import numpy as np
import math

class PupilTracker:
    """
    Neuro screening via Pupillometry using MediaPipe Face Mesh.
    Tracks Iris landmarks to estimate pupil diameter and reactivity.
    """
    def __init__(self):
        self.mp_face_mesh = mp.solutions.face_mesh
        self.face_mesh = self.mp_face_mesh.FaceMesh(
            max_num_faces=1,
            refine_landmarks=True,  # Critical for Iris landmarks
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5
        )
        
        # MediaPipe Iris landmark indices (Left Eye)
        # Center: 468, Right: 469, Top: 470, Left: 471, Bottom: 472
        self.LEFT_IRIS = [468, 469, 470, 471, 472]
        self.RIGHT_IRIS = [473, 474, 475, 476, 477]

    def process_frame(self, frame: np.ndarray):
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = self.face_mesh.process(rgb_frame)
        
        metrics = {
            "left_diameter_px": 0.0,
            "right_diameter_px": 0.0,
            "anisocoria_ratio": 0.0
        }

        if results.multi_face_landmarks:
            landmarks = results.multi_face_landmarks[0].landmark
            h, w, _ = frame.shape

            # Calculate diameters (Horizontal distance)
            l_iris_pts = [landmarks[i] for i in self.LEFT_IRIS]
            r_iris_pts = [landmarks[i] for i in self.RIGHT_IRIS]

            # 471 (Left) <-> 469 (Right) distance for Left Eye Iris
            # Note: Approximating diameter using landmark limits
            l_width = math.sqrt(
                (l_iris_pts[1].x * w - l_iris_pts[3].x * w)**2 + 
                (l_iris_pts[1].y * h - l_iris_pts[3].y * h)**2
            )
            
            r_width = math.sqrt(
                (r_iris_pts[1].x * w - r_iris_pts[3].x * w)**2 + 
                (r_iris_pts[1].y * h - r_iris_pts[3].y * h)**2
            )
            
            metrics["left_diameter_px"] = l_width
            metrics["right_diameter_px"] = r_width
            
            if r_width > 0:
                metrics["anisocoria_ratio"] = abs(l_width - r_width) / ((l_width + r_width) / 2)

        return metrics

    def close(self):
        self.face_mesh.close()
