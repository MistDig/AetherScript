"""Opt-in integration: opens the real hosted browser page, grants exact IDs, checks MCP and restart."""
import base64
import hashlib
import json
import os
import re
import secrets
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from threading import Thread
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen
from playwright.sync_api import sync_playwright, expect


def verify(command, start, stop):
    line = command("ais connect", "/connect#code=")
    link = re.search(r"https://[^\s]+/connect#code=[^\s]+", line).group()
    origin = "https://" + urlsplit(link).netloc
    callback = {}

    class Receiver(BaseHTTPRequestHandler):
        def do_GET(self):
            callback.update(parse_qs(urlsplit(self.path).query))
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Authorization returned to the test client.")
        def log_message(self, *args):
            pass

    def post(path, value, token=None, form=False):
        headers = {"Content-Type": "application/x-www-form-urlencoded" if form else "application/json",
                   "Accept": "application/json, text/event-stream", "User-Agent": "AetherScript-Browser-Test"}
        if token:
            headers["Authorization"] = "Bearer " + token
        body = urlencode(value).encode() if form else json.dumps(value).encode()
        with urlopen(Request(origin + path, data=body, headers=headers), timeout=50) as response:
            return json.load(response)

    with HTTPServer(("127.0.0.1", 0), Receiver) as server, sync_playwright() as pw:
        redirect = f"http://127.0.0.1:{server.server_port}/callback"
        Thread(target=server.serve_forever, daemon=True).start()
        client = post("/oauth/register", {"redirect_uris": [redirect], "token_endpoint_auth_method": "none"})
        verifier = secrets.token_urlsafe(48)
        challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().strip("=")
        state = secrets.token_hex(16)
        oauth = dict(client_id=client["client_id"], redirect_uri=redirect, resource=origin+"/mcp",
                     response_type="code", code_challenge_method="S256", code_challenge=challenge, state=state)
        browser = pw.chromium.launch(channel=os.environ.get("AETHER_BROWSER_CHANNEL", "msedge"), headless=True)
        page = browser.new_page()
        page.goto(link)
        expect(page.locator("#server")).to_have_text(re.compile(r"\S"))
        page.locator("#kind").select_option("codex")
        page.wait_for_timeout(500)
        # Exercise the same OAuth redirect boundary as a desktop MCP client.
        page.goto(origin+"/oauth/authorize?"+urlencode(oauth))
        expect(page.locator("#server")).to_have_text(re.compile(r"\S"))
        page.locator("#name").fill("Desktop integration")
        if page.locator("#selected").count():
            page.locator("#selected").check()
        page.locator("#ids").fill("cloud-note")
        page.locator("#approve").click()
        page.wait_for_url(redirect+"**", timeout=30000)
        assert callback.get("state") == [state]
        token = post("/oauth/token", dict(grant_type="authorization_code", code=callback["code"][0],
                     client_id=client["client_id"], redirect_uri=redirect, code_verifier=verifier, resource=origin+"/mcp"), form=True)["access_token"]
        browser.close()
        server.shutdown()

    def call(name, args):
        return post("/mcp", {"jsonrpc":"2.0","id":1,"method":"tools/call",
                    "params":{"name": name, "arguments":args}}, token)

    def status():
        for _ in range(15):
            try:
                result=call("aether_server_status", {})
                if "result" in result and not result["result"].get("isError",False) and result["result"].get("structuredContent",{}).get("generation",0)>=1:
                    return
            except HTTPError as e:
                if e.code not in (401,403,409,503): raise
            time.sleep(2)
        raise AssertionError("An approved connection could not read live status")

    status()
    from codex_hosted import discover
    discover(origin + "/mcp", token)
    def tool(name, arguments, success=True):
        reply = call(name, arguments)
        result = reply.get("result", {})
        assert "error" not in reply and (not result.get("isError", True)) == success, name
        return result.get("structuredContent", {})
    source = 'feature cloud-note version "1.0.0"\ncommand cloud-note permission "aetherscript.example.cloud" sender any {\n  arg name: text\n  arg excited: boolean = true\n  if excited {\n    send "Hello " + name + "!"\n  } else {\n    send "Hello " + name\n  }\n}\n'
    initial = tool("aether_scripts_read", {"id": "cloud-note"})
    assert not initial["exists"]
    invalid = tool("aether_scripts_validate", {"id": "cloud-note", "source": source.replace("arg name: text", "arg name: nope")}, success=False)
    assert invalid["error"]["code"] == "VALIDATION_FAILED" and "line" in invalid["error"]["message"]
    applied = tool("aether_scripts_apply", {"id": "cloud-note", "source": source, "expectedHash": "ABSENT"})
    assert applied["hash"] == applied["activeHash"]
    assert tool("aether_scripts_test", {"id": "cloud-note", "arguments": ["MistDig"], "expectedMessages": ["Hello MistDig!"]})["matched"]
    assert tool("aether_scripts_test", {"id": "cloud-note", "arguments": ["Alex", "false"], "expectedMessages": ["Hello Alex"]})["matched"]
    command('ais run cloud-note "Alex Smith"', "Hello Alex Smith!")
    tool("aether_scripts_read", {"id": "hello-ai"}, success=False)
    print("Scoped script creation, captured output and target denial: pass", flush=True)
    print("Browser approval and hosted status: pass",flush=True)
    stop()
    start()
    status()
    assert tool("aether_scripts_read", {"id": "cloud-note"})["source"] == source
    print("Approved connection and script survived Paper restart: pass",flush=True)
    command("ais disconnect", "revocation saved")
    for _ in range(15):
        try:
            reply=call("aether_server_status", {})
            if "error" in reply or reply.get("result",{}).get("isError"): return
        except HTTPError as e:
            if e.code in (401,403): return
            raise
        time.sleep(1)
    raise AssertionError("Revoked connection still returns live status")
