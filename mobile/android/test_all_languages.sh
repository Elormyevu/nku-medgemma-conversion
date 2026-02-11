#!/bin/bash
# Nku App - All 47 Languages Test Script
# Tests each language by selecting it from the picker and capturing a screenshot

set -e

ADB="/Users/elormyevudza/Documents/0AntigravityProjects/nku-impact-challenge-1335/mobile/android/platform-tools/adb"
SCREENSHOT_DIR="/Users/elormyevudza/.gemini/antigravity/brain/0694e546-118e-4608-af26-b9e5b2f649bc/language_tests"
mkdir -p "$SCREENSHOT_DIR"

# All 47 languages (9 Most Used + 38 All Languages)
MOST_USED=("English" "French" "Portuguese" "Swahili" "Twi" "Ewe" "Ga" "Hausa" "Yoruba")
ALL_LANGS=("Afrikaans" "Akan" "Amharic" "Arabic" "Bambara" "Berber" "Chewa" "Dinka" "Edo" "Fon" "Fula" "Gikuyu" "Igbo" "Kinyarwanda" "Kirundi" "Kongo" "Lingala" "Luo" "Malagasy" "Mandinka" "Mooré" "Ndebele" "Oromo" "Pedi" "Sango" "Sesotho" "Shona" "Somali" "Swati" "Tigrinya" "Tshivenda" "Tsonga" "Tswana" "Venda" "Wolof" "Xhosa" "Zulu")

# Results file
RESULTS_FILE="$SCREENSHOT_DIR/test_results.md"
echo "# Nku Language Test Results" > "$RESULTS_FILE"
echo "Tested on: $(date)" >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"
echo "| # | Language | Status | Screenshot |" >> "$RESULTS_FILE"
echo "|---|----------|--------|------------|" >> "$RESULTS_FILE"

# Function to select a language
select_language() {
    local lang="$1"
    local index="$2"
    
    echo "Testing language $index: $lang"
    
    # Open language picker (tap on dropdown)
    $ADB shell input tap 540 587
    sleep 1.5
    
    # Dump UI hierarchy
    $ADB shell uiautomator dump /sdcard/ui_dump.xml 2>/dev/null
    $ADB pull /sdcard/ui_dump.xml /tmp/ui_dump.xml 2>/dev/null
    
    # Find the language in the hierarchy and get its bounds
    BOUNDS=$(grep -o "text=\"$lang\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" /tmp/ui_dump.xml 2>/dev/null | grep -o "bounds=\"[^\"]*\"" | head -1)
    
    if [ -z "$BOUNDS" ]; then
        # Language not visible, need to scroll down
        echo "  Scrolling to find $lang..."
        for scroll in 1 2 3 4 5 6; do
            $ADB shell input swipe 540 1100 540 400 300
            sleep 0.5
            $ADB shell uiautomator dump /sdcard/ui_dump.xml 2>/dev/null
            $ADB pull /sdcard/ui_dump.xml /tmp/ui_dump.xml 2>/dev/null
            BOUNDS=$(grep -o "text=\"$lang\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" /tmp/ui_dump.xml 2>/dev/null | grep -o "bounds=\"[^\"]*\"" | head -1)
            if [ -n "$BOUNDS" ]; then
                break
            fi
        done
    fi
    
    if [ -n "$BOUNDS" ]; then
        # Extract coordinates and tap
        COORDS=$(echo "$BOUNDS" | grep -o "\[[-0-9]*,[-0-9]*\]" | head -2)
        X1=$(echo "$COORDS" | head -1 | tr -d '[]' | cut -d',' -f1)
        Y1=$(echo "$COORDS" | head -1 | tr -d '[]' | cut -d',' -f2)
        X2=$(echo "$COORDS" | tail -1 | tr -d '[]' | cut -d',' -f1)
        Y2=$(echo "$COORDS" | tail -1 | tr -d '[]' | cut -d',' -f2)
        
        CENTER_X=$(( (X1 + X2) / 2 ))
        CENTER_Y=$(( (Y1 + Y2) / 2 ))
        
        echo "  Tapping at ($CENTER_X, $CENTER_Y)"
        $ADB shell input tap $CENTER_X $CENTER_Y
        sleep 1.5
        
        # Capture screenshot
        SAFE_NAME=$(echo "$lang" | tr ' ' '_' | tr -cd '[:alnum:]_')
        SCREENSHOT="$SCREENSHOT_DIR/${index}_${SAFE_NAME}.png"
        $ADB exec-out screencap -p > "$SCREENSHOT"
        
        echo "  ✅ $lang - Screenshot saved"
        echo "| $index | $lang | ✅ Pass | ![${lang}](test_screenshots/${index}_${SAFE_NAME}.png) |" >> "$RESULTS_FILE"
        return 0
    else
        echo "  ❌ $lang - Not found in picker"
        echo "| $index | $lang | ❌ Not Found | - |" >> "$RESULTS_FILE"
        # Dismiss picker
        $ADB shell input keyevent 4
        sleep 0.5
        return 1
    fi
}

# Test all Most Used languages first
echo "=== Testing Most Used Languages ==="
count=1
for lang in "${MOST_USED[@]}"; do
    select_language "$lang" "$count"
    ((count++))
done

# Test all other languages
echo ""
echo "=== Testing All Languages ==="
for lang in "${ALL_LANGS[@]}"; do
    select_language "$lang" "$count"
    ((count++))
done

echo ""
echo "=== Testing Complete ==="
echo "Results saved to $RESULTS_FILE"
echo "Screenshots saved to $SCREENSHOT_DIR"
