"""
Remote Photoplethysmography (rPPG) Processor
Extracts heart rate from video frames by analyzing the green channel intensity variation.

Type-annotated and production-hardened version.
"""

import cv2
import numpy as np
import scipy.signal as signal
from typing import List, Optional, Tuple
from dataclasses import dataclass
import logging

logger = logging.getLogger(__name__)


@dataclass
class RPPGResult:
    """Result of rPPG analysis."""
    bpm: Optional[float]
    confidence: float
    signal_quality: str  # 'good', 'fair', 'poor', 'insufficient'
    buffer_fill_percent: float


class RPPGProcessor:
    """
    Remote Photoplethysmography (rPPG) Processor.
    Extracts heart rate from video frames by analyzing the green channel intensity variation.
    
    Attributes:
        fps: Frame rate of the video source
        min_hz: Minimum detectable frequency (corresponds to ~45 BPM)
        max_hz: Maximum detectable frequency (corresponds to ~180 BPM)
        buffer_size: Number of frames to keep in buffer (10 seconds default)
    """
    
    # Physiological limits
    MIN_BPM = 40.0
    MAX_BPM = 200.0
    
    # Signal quality thresholds
    MIN_PEAK_PROMINENCE = 0.1
    MIN_SIGNAL_VARIANCE = 1.0
    
    def __init__(self, fps: float = 30.0, buffer_seconds: float = 10.0):
        """
        Initialize the rPPG processor.
        
        Args:
            fps: Expected frames per second from the video source
            buffer_seconds: Duration of signal to buffer before analysis
        """
        if fps <= 0:
            raise ValueError("FPS must be positive")
        if buffer_seconds <= 0:
            raise ValueError("Buffer seconds must be positive")
        
        self.fps = fps
        self.min_hz = self.MIN_BPM / 60.0  # ~0.67 Hz for 40 BPM
        self.max_hz = self.MAX_BPM / 60.0  # ~3.33 Hz for 200 BPM
        self.buffer_size = int(fps * buffer_seconds)
        self.min_analysis_frames = int(fps * 5)  # Need at least 5 seconds
        
        self.signal_buffer: List[float] = []
        self._last_bpm: Optional[float] = None
        
        logger.info(f"RPPGProcessor initialized: fps={fps}, buffer_size={self.buffer_size}")
    
    def process_frame(
        self, 
        frame: np.ndarray, 
        face_roi: Optional[np.ndarray] = None
    ) -> RPPGResult:
        """
        Process a single frame and return the estimated Heart Rate.
        
        Args:
            frame: BGR image frame from camera
            face_roi: Optional pre-extracted face region of interest
        
        Returns:
            RPPGResult with BPM estimate, confidence, and signal quality
        """
        if frame is None or frame.size == 0:
            logger.warning("Received empty frame")
            return RPPGResult(
                bpm=None,
                confidence=0.0,
                signal_quality='insufficient',
                buffer_fill_percent=self._get_buffer_fill_percent()
            )
        
        # Extract ROI
        roi = self._extract_roi(frame, face_roi)
        
        if roi is None or roi.size == 0:
            return RPPGResult(
                bpm=None,
                confidence=0.0,
                signal_quality='insufficient',
                buffer_fill_percent=self._get_buffer_fill_percent()
            )
        
        # Extract green channel mean (strongest PPG signal in camera)
        green_mean = self._extract_green_mean(roi)
        
        # Update buffer
        self.signal_buffer.append(green_mean)
        if len(self.signal_buffer) > self.buffer_size:
            self.signal_buffer.pop(0)
        
        # Check if we have enough data
        if len(self.signal_buffer) < self.min_analysis_frames:
            return RPPGResult(
                bpm=None,
                confidence=0.0,
                signal_quality='insufficient',
                buffer_fill_percent=self._get_buffer_fill_percent()
            )
        
        # Analyze signal
        try:
            bpm, confidence, quality = self._analyze_signal()
            self._last_bpm = bpm
            
            return RPPGResult(
                bpm=bpm,
                confidence=confidence,
                signal_quality=quality,
                buffer_fill_percent=self._get_buffer_fill_percent()
            )
        except Exception as e:
            logger.error(f"Signal analysis failed: {e}")
            # L-4 fix: Return None instead of stale _last_bpm
            # In clinical context, stale data could mask tachycardia/bradycardia
            return RPPGResult(
                bpm=None,
                confidence=0.0,
                signal_quality='poor',
                buffer_fill_percent=self._get_buffer_fill_percent()
            )
    
    def _extract_roi(
        self, 
        frame: np.ndarray, 
        face_roi: Optional[np.ndarray]
    ) -> Optional[np.ndarray]:
        """Extract region of interest from frame."""
        if face_roi is not None:
            return face_roi
        
        # Default: use center region (approximate forehead/cheek area)
        h, w = frame.shape[:2]
        
        if h < 50 or w < 50:
            return None
        
        # Center crop with 50% margins
        margin_h, margin_w = h // 4, w // 4
        roi = frame[margin_h:h-margin_h, margin_w:w-margin_w]
        
        return roi if roi.size > 0 else None
    
    def _extract_green_mean(self, roi: np.ndarray) -> float:
        """Extract mean green channel value from ROI."""
        # OpenCV uses BGR ordering
        if len(roi.shape) == 3 and roi.shape[2] >= 2:
            green_channel = roi[:, :, 1]
        else:
            green_channel = roi
        
        return float(np.mean(green_channel))
    
    def _analyze_signal(self) -> Tuple[Optional[float], float, str]:
        """
        Analyze the signal buffer to extract heart rate.
        
        Returns:
            Tuple of (bpm, confidence, quality_string)
        """
        data = np.array(self.signal_buffer)
        
        # Check signal variance
        variance = np.var(data)
        if variance < self.MIN_SIGNAL_VARIANCE:
            return None, 0.0, 'poor'
        
        # Detrend to remove baseline drift
        data = signal.detrend(data)
        
        # Apply bandpass filter
        try:
            filtered = self._bandpass_filter(data)
        except Exception as e:
            logger.warning(f"Bandpass filter failed: {e}")
            return None, 0.0, 'poor'
        
        # FFT analysis
        bpm, confidence = self._fft_analysis(filtered)
        
        # Determine quality
        if confidence >= 0.7:
            quality = 'good'
        elif confidence >= 0.4:
            quality = 'fair'
        else:
            quality = 'poor'
        
        return bpm, confidence, quality
    
    def _bandpass_filter(self, data: np.ndarray) -> np.ndarray:
        """Apply Butterworth bandpass filter."""
        nyquist = 0.5 * self.fps
        
        # Clamp frequencies to valid range
        low = max(self.min_hz / nyquist, 0.01)
        high = min(self.max_hz / nyquist, 0.99)
        
        if low >= high:
            return data
        
        b, a = signal.butter(3, [low, high], btype='band')
        return signal.filtfilt(b, a, data, padlen=min(3 * max(len(a), len(b)), len(data) - 1))
    
    def _fft_analysis(self, data: np.ndarray) -> Tuple[Optional[float], float]:
        """Perform FFT to find dominant frequency."""
        n = len(data)
        
        if n < 2:
            return None, 0.0
        
        # Compute FFT
        freqs = np.fft.rfftfreq(n, d=1/self.fps)
        magnitude = np.abs(np.fft.rfft(data))
        
        # Mask to valid frequency range
        mask = (freqs >= self.min_hz) & (freqs <= self.max_hz)
        valid_freqs = freqs[mask]
        valid_mags = magnitude[mask]
        
        if len(valid_mags) == 0:
            return None, 0.0
        
        # Find peak
        peak_idx = np.argmax(valid_mags)
        peak_freq = valid_freqs[peak_idx]
        peak_mag = valid_mags[peak_idx]
        
        # Calculate confidence based on peak prominence
        if len(valid_mags) > 1:
            # Compare peak to median
            median_mag = np.median(valid_mags)
            if median_mag > 0:
                prominence = (peak_mag - median_mag) / peak_mag
                confidence = min(prominence, 1.0)
            else:
                confidence = 0.5
        else:
            confidence = 0.5
        
        bpm = peak_freq * 60.0
        
        # Sanity check
        if bpm < self.MIN_BPM or bpm > self.MAX_BPM:
            return None, 0.0
        
        return round(bpm, 1), round(confidence, 2)
    
    def _get_buffer_fill_percent(self) -> float:
        """Get buffer fill percentage."""
        return round(100.0 * len(self.signal_buffer) / self.buffer_size, 1)
    
    def reset(self) -> None:
        """Clear the signal buffer."""
        self.signal_buffer.clear()
        self._last_bpm = None
        logger.info("rPPG buffer reset")
    
    def get_status(self) -> dict:
        """Get processor status."""
        return {
            "buffer_fill_percent": self._get_buffer_fill_percent(),
            "fps": self.fps,
            "last_bpm": self._last_bpm,
            "min_frames_needed": self.min_analysis_frames,
            "current_frames": len(self.signal_buffer)
        }
