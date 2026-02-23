import xml.etree.ElementTree as ET
import subprocess
import time
import re

adb = "/Users/elormyevudza/Library/Android/sdk/platform-tools/adb"

def dump_ui():
    subprocess.run([adb, "shell", "uiautomator", "dump", "/sdcard/window_dump.xml"], capture_output=True)
    subprocess.run([adb, "pull", "/sdcard/window_dump.xml", "window_dump.xml"], capture_output=True)
    try:
        tree = ET.parse("window_dump.xml")
        return tree.getroot()
    except Exception:
        return None

def bounds_to_center(bounds_str):
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds_str)
    if not m: return 0,0
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1+x2)//2, (y1+y2)//2

def tap(x, y):
    subprocess.run([adb, "shell", "input", "tap", str(x), str(y)])

def tap_node_with_text(root, text_match):
    if root is None: return False
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        desc = node.attrib.get('content-desc', '')
        if text_match.lower() in text.lower() or text_match.lower() in desc.lower():
            bounds = node.attrib.get('bounds')
            if bounds:
                x, y = bounds_to_center(bounds)
                print(f"[{text_match}] Found! Tapping at {x}, {y}")
                tap(x, y)
                return True
    return False

def type_text(text):
    for word in text.split(" "):
        subprocess.run([adb, "shell", "input", "text", word])
        subprocess.run([adb, "shell", "input", "keyevent", "62"])

print("Starting app...")
subprocess.run([adb, "shell", "am", "start", "-n", "com.nku.app/.MainActivity"])
time.sleep(3)

root = dump_ui()
for _ in range(3):
    if tap_node_with_text(root, "While using the app") or tap_node_with_text(root, "Allow"):
        time.sleep(2)
        root = dump_ui()

# Give UI a moment
time.sleep(2)

print("Starting screen recording...")
# Kill any existing screen recording
subprocess.run([adb, "shell", "pkill", "-INT", "screenrecord"])
time.sleep(1)
# Start recording
subprocess.Popen([adb, "shell", "screenrecord", "--time-limit", "180", "/sdcard/triage_video.mp4"])
time.sleep(2)

root = dump_ui()
if not tap_node_with_text(root, "Triage"):
    print("Could not find Triage tab.")
time.sleep(2)

root = dump_ui()
found_edit = False
if root is not None:
    for node in root.iter('node'):
        text = node.attrib.get('text', '')
        if node.attrib.get('class') == 'android.widget.EditText' or "Symptoms" in text or "Type symptoms" in text:
            bounds = node.attrib.get('bounds')
            if bounds:
                x, y = bounds_to_center(bounds)
                print(f"Found input field! Tapping at {x}, {y}")
                tap(x, y)
                found_edit = True
                break

time.sleep(1)
if found_edit:
    print("Typing symptoms...")
    type_text("Severe fever chills vomiting jaundice 120bpm 85% pallor")
    time.sleep(2)
    subprocess.run([adb, "shell", "input", "keyevent", "4"]) # Hide keyboard
    time.sleep(2)
    
    # Scroll slightly just in case the button is off screen
    subprocess.run([adb, "shell", "input", "swipe", "500", "1500", "500", "500", "500"])
    time.sleep(2)

    root = dump_ui()
    if not tap_node_with_text(root, "Run Triage Assessment"):
        if not tap_node_with_text(root, "Run"):
            print("Could not find the Run button!")
    
    print("Waiting for MedGemma inference to complete...")
    max_wait = 600
    completed = False
    for i in range(max_wait // 2):
        time.sleep(2)
        subprocess.run([adb, "shell", "input", "swipe", "500", "2000", "500", "500", "300"])
        time.sleep(1)
        root = dump_ui()
        xml_str = ET.tostring(root, encoding='utf8').decode('utf8')
        if 'text="Urgency: Immediate"' in xml_str or 'text="Primary Concerns"' in xml_str or 'text="Recommendation"' in xml_str:
            print("Inference completed! Found results card.")
            completed = True
            break
        print(f"Waiting... ({i*2}s elapsed)")
    
    if not completed:
        print("Timed out waiting for inference to finish.")

    print("Giving the user 5 seconds to view the results card...")
    time.sleep(5) 
    
    print("Stopping screen record...")
    subprocess.run([adb, "shell", "pkill", "-INT", "screenrecord"])
    time.sleep(15)
    
    print("Pulling video...")
    subprocess.run([adb, "pull", "/sdcard/triage_video.mp4", "/Users/elormyevudza/.gemini/antigravity/brain/5ed3604d-8e50-40e0-8fe2-baebd68722c0/triage_video.mp4"])
    print("Video successfully captured and pulled!")
    
    print("Taking final screenshot...")
    subprocess.run([adb, "shell", "screencap", "-p", "/sdcard/triage_result.png"])
    subprocess.run([adb, "pull", "/sdcard/triage_result.png", "/Users/elormyevudza/.gemini/antigravity/brain/5ed3604d-8e50-40e0-8fe2-baebd68722c0/triage_result.png"])
    print("Screenshot successfully captured and pulled!")
else:
    print("Could not find EditText.")
    subprocess.run([adb, "shell", "pkill", "-INT", "screenrecord"])
