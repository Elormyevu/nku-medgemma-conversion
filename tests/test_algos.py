"""
Algorithm Prototype Tests — Python Signal Processing Validators

These tests validate the PYTHON algorithm prototypes (src/rppg/, src/neuro/),
NOT the production Kotlin implementations (mobile/android/app/src/main/java/).
The Kotlin production code has its own JVM-side tests in app/src/test/.

Purpose: Verify signal processing correctness during R&D before porting to Kotlin.
"""

import unittest
from unittest.mock import MagicMock
import sys
import os
import numpy as np

# Add project root to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

# Mock modules if they are missing
try:
    import cv2
except ImportError:
    sys.modules['cv2'] = MagicMock()
    sys.modules['cv2'].cvtColor = MagicMock(return_value=np.zeros((100,100,3)))
    sys.modules['cv2'].COLOR_BGR2RGB = 1

try:
    import scipy.signal as signal
except ImportError:
    sys.modules['scipy.signal'] = MagicMock()
    sys.modules['scipy'] = MagicMock()
    sys.modules['scipy'].signal = MagicMock()

try:
    import mediapipe as mp
    # Newer mediapipe versions may not have 'solutions' attribute
    if not hasattr(mp, 'solutions'):
        raise ImportError("mediapipe.solutions not available")
except ImportError:
    mp = MagicMock()
    sys.modules['mediapipe'] = mp

    # Mock FaceMesh
    mock_face_mesh = MagicMock()
    mock_face_mesh.process.return_value = MagicMock(multi_face_landmarks=[
        MagicMock(landmark=[MagicMock(x=0.5, y=0.5) for _ in range(500)])
    ])
    mp.solutions.face_mesh.FaceMesh.return_value = mock_face_mesh


from src.rppg.processor import RPPGProcessor
from src.neuro.pupil_tracker import PupilTracker

class TestAlgos(unittest.TestCase):
    def test_rppg_instantiation(self):
        """Test RPPGProcessor initializes and processes a frame."""
        processor = RPPGProcessor(fps=30.0)
        # Create a red/green dummy frame
        frame = np.random.randint(0, 255, (224, 224, 3), dtype=np.uint8)
        result = processor.process_frame(frame)
        # Returns RPPGResult with bpm=None when buffer not full
        self.assertIsNotNone(result)  # Should return an RPPGResult, not None
        self.assertIsNone(result.bpm)  # bpm should be None (insufficient data)
        self.assertEqual(result.signal_quality, 'insufficient')
        print("RPPGProcessor: Instantiation and single frame OK.")

    def test_pupil_tracker_instantiation(self):
        """Test PupilTracker initializes and tracks."""
        tracker = PupilTracker()
        frame = np.random.randint(0, 255, (480, 640, 3), dtype=np.uint8)
        metrics = tracker.process_frame(frame)
        self.assertIn("left_diameter_px", metrics)
        tracker.close()
        print("PupilTracker: Instantiation and inference OK (Mocked).")

if __name__ == '__main__':
    unittest.main()
