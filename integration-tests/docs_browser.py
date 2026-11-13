"""Check the rendered docs copy/download controls with Edge; requires Playwright."""
import functools
import re
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
from playwright.sync_api import sync_playwright, expect

root = Path(__file__).resolve().parents[1]
dist = root / "site/.vitepress/dist"
version = re.search(r"^version=(.+)$", (root/"gradle.properties").read_text(), re.M)[1].strip()


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        self.path = self.path.removeprefix("/AetherScript")
        super().do_GET()
    def log_message(self, *args):
        pass


with ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Handler, directory=str(dist))) as server:
    Thread(target=server.serve_forever, daemon=True).start()
    origin = f"http://127.0.0.1:{server.server_port}"
    with sync_playwright() as pw:
        browser = pw.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(viewport={"width":1440,"height":1000}, permissions=["clipboard-read", "clipboard-write"])
        page = context.new_page()
        page.goto(origin + "/AetherScript/")
        page.get_by_role("button", name="Copy docs link for AI").first.click()
        assert page.evaluate("navigator.clipboard.readText()") == origin + f"/AetherScript/releases/{version}/llms-full.txt"
        expect(page.get_by_role("status").first).to_have_text("Copied")
        assert page.url == origin + "/AetherScript/"
        for link in page.get_by_role("link", name="Download docs", exact=True).all():
            assert link.get_attribute("href") == f"/AetherScript/releases/{version}/llms-full.txt"
        page.goto(origin + "/AetherScript/guide/language.html")
        expect(page.get_by_role("button", name="Copy docs link for AI").first).to_be_visible()
        browser.close()
    server.shutdown()
print("Rendered homepage and guide copy/download controls: pass")
