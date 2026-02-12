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
*   `MainActivity.kt`: Entry point, Compose UI host, permission handling.
*   `NkuInferenceEngine.kt`: MedGemma orchestration (mmap loading, retry/backoff).\n*   `NkuTranslator.kt`: ML Kit on-device translation wrapper (59 languages + cloud fallback).
*   `RPPGProcessor.kt`: Heart rate extraction via rPPG (camera-based).
*   `PallorDetector.kt`: Anemia screening via conjunctival pallor analysis.
*   `JaundiceDetector.kt`: Jaundice screening via scleral icterus analysis.
*   `EdemaDetector.kt`: Preeclampsia screening via facial edema geometry.
*   `SensorFusion.kt`: Aggregates vital signs from all four detectors.
*   `ClinicalReasoner.kt`: MedGemma triage prompts + WHO/IMCI rule-based fallback.
*   `LocalizedStrings.kt`: 46-language UI strings (14 Tier 1, 32 Tier 2).
*   `screens/`: Extracted screen composables (HomeScreen, CardioScreen, AnemiaScreen, JaundiceScreen, PreeclampsiaScreen, TriageScreen).

## Troubleshooting
*   **Permissions**: If the camera doesn't open, ensuring you have granted Camera and Internet permissions in the dialog.
*   **Gradle Errors**: Try `File > Invalidate Caches / Restart` if sync fails.
