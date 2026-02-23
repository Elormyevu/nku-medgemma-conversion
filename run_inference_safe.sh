#!/bin/bash
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell am force-stop com.nku.app
sleep 2
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell am start -n com.nku.app/.MainActivity
sleep 5

# Triage tab
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 884 2164
sleep 2

# Start recording safely (60 seconds max)
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell screenrecord --time-limit 60 --size 720x1560 /sdcard/medgemma_safe.mp4 &
RECORD_PID=$!
sleep 2

# Tap text field
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 1512
sleep 1

# Inject symptom text
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input text "Patient%sexperiencing%shigh%sfever%sand%sbody%saches%sfor%s3%sdays."
sleep 1

# Fake Enter
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input keyevent 66
sleep 1

# Dismiss keyboard explicitly
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input keyevent 4
sleep 2

# Tap the Add Symptom (+) button
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 926 1512
sleep 2

# Scroll slightly (some devices need this slightly higher)
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input swipe 540 1700 540 600 500
sleep 2

# Tap the Run Triage button
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell input tap 540 1715
sleep 1

echo "Waiting for MedGemma inference (50 seconds)..."
sleep 50

# Take screenshot of the generated response
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb shell screencap -p /sdcard/inference_screenshot_safe.png

# The screenrecord should have auto-terminated at 60s, just pulling the artifacts now
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb pull /sdcard/medgemma_safe.mp4 video_assets/
/Users/elormyevudza/Library/Android/sdk/platform-tools/adb pull /sdcard/inference_screenshot_safe.png video_assets/
cp video_assets/medgemma_safe.mp4 ~/Desktop/
cp video_assets/inference_screenshot_safe.png ~/Desktop/
