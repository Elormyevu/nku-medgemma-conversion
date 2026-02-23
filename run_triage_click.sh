#!/bin/bash
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell screenrecord --time-limit 80 --size 720x1560 /sdcard/medgemma_inference_final.mp4 &
RECORD_PID=$!
sleep 2

# Tap Run Triage Assessment (Center is 540 1953 from triage_with_chip.xml)
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 1953

echo "Waiting for MedGemma inference (55 seconds)..."
sleep 55

# Take screenshot of the generated response
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/inference_screenshot_final.png

sleep 10

# Pull artifacts
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb pull /sdcard/medgemma_inference_final.mp4 video_assets/
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb pull /sdcard/inference_screenshot_final.png video_assets/
cp video_assets/medgemma_inference_final.mp4 ~/Desktop/
cp video_assets/inference_screenshot_final.png ~/Desktop/
