from playwright.sync_api import sync_playwright
import time
import os

def main():
    try:
        with sync_playwright() as p:
            print("Connecting to browser...")
            browser = p.chromium.connect_over_cdp("http://localhost:9222")
            for context in browser.contexts:
                for page in context.pages:
                    if "Scenebuilder" in page.title() or "Flow" in page.title():
                        print(f"Found Flow Page: {page.url} | {page.title()}")
                        
                        try:
                            # Use expect_download to capture the actual file
                            with page.expect_download(timeout=120000) as download_info:
                                print("Clicking download link...")
                                # It's a span or div with text Download
                                locators = page.locator("text='Download'").all()
                                if locators:
                                    # click the last one which is usually the popup
                                    locators[-1].click(force=True)
                                else:
                                    print("Could not find 'Download' text, trying a generic click on the right side.")
                                    page.mouse.click(890, 210)
                                
                            download = download_info.value
                            print(f"Downloaded temporarily to: {download.path()}")
                            dest = "/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/medgemma_nku_submission.mp4"
                            download.save_as(dest)
                            print(f"Saved successfully to: {dest}")
                            return
                        except Exception as e:
                            print(f"Failed to capture download: {e}")
                            
                            # Fallback: look for video src
                            print("Looking for <video> tags as fallback...")
                            videos = page.locator("video").all()
                            for v in videos:
                                src = v.get_attribute("src")
                                print("Video src:", src)
                            return
            print("Flow page not found among open tabs.")
    except Exception as e:
        print("Error:", e)

if __name__ == "__main__":
    main()
