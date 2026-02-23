#!/bin/bash
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell screenrecord --size 720x1560 /sdcard/medgemma_inference_final.mp4 &
RECORD_PID=$!
sleep 2

# Tap text field
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 1512
sleep 1

# Inject symptom text (the URL-encoded %s are spaces for ADB input)
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input text "Patient%sexperiencing%shigh%sfever%sand%sbody%saches%sfor%s3%sdays."
sleep 1

# Tap the Add Symptom (+) button next to it (X=926, Y=1512)
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 926 1512
sleep 1

# Tap Title area to hide keyboard just in case
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 400
sleep 2

# Tap the Run Triage button
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 2014
sleep 1
# Backup tap inside the button bounds
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 1990

echo "Waiting for MedGemma inference..."
sleep 45

# Take screenshot of the generated response
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/inference_screenshot_final.png

# Stop recording
kill $RECORD_PID || true
sleep 2

# Pull artifacts
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb pull /sdcard/medgemma_inference_final.mp4 video_assets/
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb pull /sdcard/inference_screenshot_final.png video_assets/
cp video_assets/medgemma_inference_final.mp4 ~/Desktop/
cp video_assets/inference_screenshot_final.png ~/Desktop/
