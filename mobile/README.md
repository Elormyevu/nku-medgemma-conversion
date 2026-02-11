# Nku - Mobile Application

This directory contains the Android application source code for **Nku** (The Sensorless Sentinel).

## Prerequisites
*   **Android Studio**: Hedgehog (2023.1.1) or later recommended.
*   **JDK**: Version 17.
*   **Android SDK**: API Level 34 (Upside Down Cake).

## Open Project
1.  Open Android Studio.
2.  Select **Open** or **Import Project**.
3.  Navigate to this `mobile/` directory and select it (the folder containing `settings.gradle`).

## Build & Run
1.  Wait for Gradle Sync to complete.
2.  Connect a physical Android device (Developer Mode enabled) or create an Emulator (e.g., Pixel 5, API 34).
3.  Click the **Run** button (Green Arrow).

## Key Files
*   `MainActivity.kt`: Entry point, permission handling.
*   `CameraFragment.kt`: CameraX setup, binds `ImageAnalyzer`.
*   `ImageAnalyzer.kt`: Core logic. Runs MediaPipe FaceLandmarker and state machine.
*   `NkuBrain.kt`: The Intelligence Engine (stubbed for PaliGemma).
*   `OverlayView.kt`: Custom View for AR wireframes and result dashboard.

## Troubleshooting
*   **Permissions**: If the camera doesn't open, ensuring you have granted Camera and Internet permissions in the dialog.
*   **Gradle Errors**: Try `File > Invalidate Caches / Restart` if sync fails.
