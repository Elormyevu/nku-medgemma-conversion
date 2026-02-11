import cv2
import mediapipe as mp
import numpy as np

class ROIExtractor:
    """
    Extracts medically relevant Regions of Interest (ROIs) using MediaPipe.
    - Face: Eyes (Anemia), Forehead/Mouth (Stroke)
    - Hands: Palm (Anemia)
    
    This Python logic serves as the blueprint for the Android Java/Kotlin implementation.
    """
    
    def __init__(self):
        self.mp_face_mesh = mp.solutions.face_mesh
        self.face_mesh = self.mp_face_mesh.FaceMesh(
            static_image_mode=True,
            max_num_faces=1,
            refine_landmarks=True,
            min_detection_confidence=0.5
        )
        
        self.mp_hands = mp.solutions.hands
        self.hands = self.mp_hands.Hands(
            static_image_mode=True,
            max_num_hands=1,
            min_detection_confidence=0.5
        )

        # Key Landmarks (MediaPipe 468 map)
        # Left Eye Indices (Lower Eyelid for Conjunctiva)
        self.LEFT_EYE_LOWER = [33, 7, 163, 144, 145, 153, 154, 155, 133]
        # Right Eye Indices
        self.RIGHT_EYE_LOWER = [362, 382, 381, 380, 374, 373, 390, 249, 263]
        
    def _get_bounding_box(self, landmarks, image_shape):
        h, w = image_shape[:2]
        x_min, y_min = w, h
        x_max, y_max = 0, 0
        
        for idx in landmarks:
            # MediaPipe landmarks are normalized [0,1]
            # Convert to pixel coordinates
            # Note: idx is already the landmark object from results, NOT the index integer
            # Wait, results.multi_face_landmarks[0].landmark[i] is the object.
            # So if passing raw landmark objects:
            cx, cy = int(idx.x * w), int(idx.y * h)
            
            x_min = min(x_min, cx)
            y_min = min(y_min, cy)
            x_max = max(x_max, cx)
            y_max = max(y_max, cy)
            
        # Add padding (15%)
        pad_x = int((x_max - x_min) * 0.15)
        pad_y = int((y_max - y_min) * 0.15)
        
        return (
            max(0, x_min - pad_x),
            max(0, y_min - pad_y),
            min(w, x_max + pad_x),
            min(h, y_max + pad_y)
        )

    def extract_eye_regions(self, image):
        """
        Returns (left_eye_img, right_eye_img) focusing on the lower conjunctiva.
        """
        results = self.face_mesh.process(cv2.cvtColor(image, cv2.COLOR_BGR2RGB))
        
        if not results.multi_face_landmarks:
            print("No face detected.")
            return None, None
            
        landmarks = results.multi_face_landmarks[0].landmark
        h, w = image.shape[:2]

        # Extract Left
        left_pts = [landmarks[i] for i in self.LEFT_EYE_LOWER]
        lx1, ly1, lx2, ly2 = self._get_bounding_box(left_pts, image.shape)
        left_crop = image[ly1:ly2, lx1:lx2]
        
        # Extract Right
        right_pts = [landmarks[i] for i in self.RIGHT_EYE_LOWER]
        rx1, ry1, rx2, ry2 = self._get_bounding_box(right_pts, image.shape)
        right_crop = image[ry1:ry2, rx1:rx2]
        
        return left_crop, right_crop

    def extract_palm_region(self, image):
        """
        Returns the center of the palm.
        """
        results = self.hands.process(cv2.cvtColor(image, cv2.COLOR_BGR2RGB))
        
        if not results.multi_hand_landmarks:
            print("No hands detected.")
            return None
            
        landmarks = results.multi_hand_landmarks[0].landmark
        
        # Palm Center roughly average of Wrist(0), IndexMCP(5), PinkyMCP(17)
        palm_indices = [0, 5, 17]
        palm_pts = [landmarks[i] for i in palm_indices]
        
        x1, y1, x2, y2 = self._get_bounding_box(palm_pts, image.shape)
        return image[y1:y2, x1:x2]

    def close(self):
        self.face_mesh.close()
        self.hands.close()

if __name__ == "__main__":
    # Test stub
    pass
