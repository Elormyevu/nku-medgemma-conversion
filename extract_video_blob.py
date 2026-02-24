from playwright.sync_api import sync_playwright
import base64

def main():
    try:
        with sync_playwright() as p:
            browser = p.chromium.connect_over_cdp("http://localhost:9222")
            for context in browser.contexts:
                for page in context.pages:
                    if "Scenebuilder" in page.title() or "Flow" in page.title():
                        print(f"Found Flow Page: {page.url}")
                        
                        videos = page.locator("video").all()
                        if not videos:
                            print("No videos found.")
                            return
                            
                        # Grab the first video blob (often the compiled one or timeline preview)
                        blob_url = videos[0].get_attribute("src")
                        print(f"Found blob URL: {blob_url}")
                        
                        if not blob_url or not blob_url.startswith("blob:"):
                            print("Not a blob url.")
                            return
                            
                        print("Extracting blob data to base64 via JS...")
                        jscode = """
                        async (url) => {
                            const response = await fetch(url);
                            const blob = await response.blob();
                            return new Promise((resolve, reject) => {
                                const reader = new FileReader();
                                reader.onloadend = () => resolve(reader.result);
                                reader.onerror = reject;
                                reader.readAsDataURL(blob);
                            });
                        }
                        """
                        b64_data = page.evaluate(jscode, blob_url)
                        
                        # b64_data looks like 'data:video/mp4;base64,...'
                        if "base64," in b64_data:
                            header, encoded = b64_data.split("base64,", 1)
                        else:
                            encoded = b64_data
                            
                        dataBytes = base64.b64decode(encoded)
                        dest = "/Users/elormyevudza/Library/CloudStorage/GoogleDrive-wizzyevu@gmail.com/My Drive/0AntigravityProjects/nku-impact-challenge-1335/medgemma_nku_submission.mp4"
                        with open(dest, "wb") as f:
                            f.write(dataBytes)
                            
                        print(f"Successfully saved {len(dataBytes)} bytes to {dest}")
                        return
            print("Done inspecting.")
    except Exception as e:
        print("Error:", e)

if __name__ == "__main__":
    main()
