from playwright.sync_api import sync_playwright

def main():
    try:
        with sync_playwright() as p:
            browser = p.chromium.connect_over_cdp("http://localhost:9222")
            for context in browser.contexts:
                for page in context.pages:
                    if "Scenebuilder" in page.title() or "Flow" in page.title():
                        print(f"Found Flow Page: {page.url}")
                        
                        # Inspect 'Download' elements
                        js = """
                        () => {
                            const elements = Array.from(document.querySelectorAll('*'));
                            const downloadBtns = elements.filter(el => el.textContent && el.textContent.trim() === 'Download');
                            return downloadBtns.map(el => {
                                return {
                                    tagName: el.tagName,
                                    className: el.className,
                                    href: el.href || null,
                                    html: el.outerHTML
                                };
                            });
                        }
                        """
                        res = page.evaluate(js)
                        for r in res:
                            print(f"Found Download element: {r}")
                            
                        # Also check for <video> tags
                        videos = page.locator("video").all()
                        for i, v in enumerate(videos):
                            print(f"Video {i} src: {v.get_attribute('src')}")
                            
            print("Done inspecting.")
    except Exception as e:
        print("Error:", e)

if __name__ == "__main__":
    main()
