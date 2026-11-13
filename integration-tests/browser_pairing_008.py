"""Live 0.0.8 browser regression: selected and full grants, web OAuth credentials, restart and revoke."""
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
    def connect():
        line = command("ais connect", "/connect#code=")
        return re.search(r"https://[^\s]+/connect#code=[^\s]+", line).group()

    superseded = connect()
    link = connect()
    origin = "https://" + urlsplit(link).netloc

    def http(path, value=None, token=None, form=False, web=False):
        headers = {"Accept": "application/json, text/event-stream", "User-Agent": "AetherScript-Release-Check"}
        body = None
        if value is not None:
            headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
            body = urlencode(value).encode() if form else json.dumps(value).encode()
        if token:
            headers["Authorization"] = "Bearer " + token
        if web:
            headers["Origin"] = "https://gemini.google.com"
        with urlopen(Request(origin+path, data=body, headers=headers), timeout=50) as response:
            return json.load(response)

    metadata = http("/.well-known/oauth-authorization-server")
    protected = http("/.well-known/oauth-protected-resource")
    assert metadata["registration_endpoint"].startswith(origin)
    assert protected["authorization_servers"] == [origin]
    assert {"none","client_secret_basic","client_secret_post"} <= set(metadata["token_endpoint_auth_methods_supported"])
    old_code = parse_qs(urlsplit(superseded).fragment)["code"][0]
    try:
        http("/v2/browser/start", {"code":old_code})
        raise AssertionError("Superseded console link remained usable")
    except HTTPError as failure:
        assert failure.code == 401
    print("Metadata and superseded console link: pass", flush=True)

    callback = {}
    class Receiver(BaseHTTPRequestHandler):
        def do_GET(self):
            callback.update(parse_qs(urlsplit(self.path).query))
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Authorization returned to the test client.")
        def log_message(self,*args):
            pass

    with HTTPServer(("127.0.0.1",0), Receiver) as receiver, sync_playwright() as pw:
        Thread(target=receiver.serve_forever,daemon=True).start()
        browser = pw.chromium.launch(channel=os.environ.get("AETHER_BROWSER_CHANNEL","msedge"),headless=True)

        def approve(url, full, web):
            redirect = "https://gemini.google.com/aetherscript-test/callback" if web else f"http://127.0.0.1:{receiver.server_port}/callback"
            client = http("/register", {"redirect_uris":[redirect],
                "token_endpoint_auth_method":"client_secret_post" if web else "none"},web=web)
            assert client["client_id"]
            if web:
                assert client["client_secret"]
            verifier=secrets.token_urlsafe(48)
            challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().strip("=")
            state=secrets.token_hex(16)
            fields=dict(client_id=client["client_id"],redirect_uri=redirect,resource=origin+"/mcp",
                response_type="code",code_challenge_method="S256",code_challenge=challenge,state=state)
            page=browser.new_page()
            if web:
                # Do not send a test authorization code to Google's actual service.
                page.route("https://gemini.google.com/**",lambda route: route.fulfill(status=200,body="Returned to web client test."))
            page.goto(url)
            expect(page.locator("#server")).to_have_text(re.compile(r"\S"))
            if not web:
                page.locator("details summary").click()
                page.locator("#callbacks").fill(redirect)
                page.locator("#auth-method").select_option("client_secret_basic")
                page.locator("#register").click()
                expect(page.locator("#client-credentials")).to_contain_text("client_id")
                manual = json.loads(page.locator("#client-credentials").inner_text())
                assert manual["client_id"] and manual["client_secret"]
                assert manual["token_endpoint_auth_method"] == "client_secret_basic"
                print("Browser fallback client registration: pass", flush=True)
            page.locator("#kind").select_option("gemini" if web else "codex")
            page.wait_for_timeout(700)
            page.goto(origin+"/oauth/authorize?"+urlencode(fields))
            expect(page.locator("#server")).to_have_text(re.compile(r"\S"))
            page.locator("#name").fill("Web connector test" if web else "Codex selected")
            page.locator("#all" if full else "#selected").check()
            if not full:
                page.locator("#ids").fill("cloud-note")
            if full:
                expect(page.get_by_text(re.compile("existing.*script",re.I)).first).to_be_visible()
            page.locator("#approve").click()
            page.wait_for_url(redirect+"**",timeout=30000)
            returned=parse_qs(urlsplit(page.url).query)
            assert returned["state"] == [state]
            body=dict(grant_type="authorization_code",code=returned["code"][0],client_id=client["client_id"],
                redirect_uri=redirect,code_verifier=verifier,resource=origin+"/mcp")
            if web:
                body["client_secret"]=client["client_secret"]
            token=http("/oauth/token",body,form=True,web=web)["access_token"]
            page.close()
            return token

        selected=approve(link,False,False)

        def call(token,name,arguments,success=True):
            answer=http("/mcp",{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":name,"arguments":arguments}},token)
            result=answer.get("result",{})
            assert "error" not in answer and (not result.get("isError",True))==success,name
            return result.get("structuredContent",{})

        def status(token):
            for _ in range(15):
                try:
                    result=http("/mcp",{"jsonrpc":"2.0","id":1,"method":"tools/call",
                        "params":{"name":"aether_server_status","arguments":{}}},token)
                    if "result" in result and not result["result"].get("isError",False):
                        return
                except HTTPError as failure:
                    if failure.code not in (401,403,409,503):raise
                time.sleep(2)
            raise AssertionError("Approval did not reach Paper")

        status(selected)
        from codex_hosted import discover
        discover(origin+"/mcp",selected)
        source='feature cloud-note version "1.0.0"\ncommand cloud-note permission "aetherscript.example.cloud" sender any {\n  send "Selected access"\n}\n'
        created=call(selected,"aether_scripts_apply",{"id":"cloud-note","source":source,"expectedHash":"ABSENT"})
        call(selected,"aether_scripts_read",{"id":"other-note"},success=False)

        full=approve(connect(),True,True)
        status(full)
        status(selected)
        edited=source.replace("Selected access","Full access")
        changed=call(full,"aether_scripts_apply",{"id":"cloud-note","source":edited,"expectedHash":created["hash"]})
        assert changed["activeHash"]==changed["hash"]
        assert call(full,"aether_scripts_test",{"id":"cloud-note","expectedMessages":["Full access"]})["matched"]
        other=source.replace("cloud-note","other-note").replace("Selected access","Created another script")
        call(full,"aether_scripts_apply",{"id":"other-note","source":other,"expectedHash":"ABSENT"})
        assert {"cloud-note","other-note"} <= set(call(full,"aether_scripts_list",{})["ids"])
        call(selected,"aether_scripts_read",{"id":"other-note"},success=False)
        print("Web confidential OAuth, full access, existing edits, new IDs and retained restrictions: pass",flush=True)
        browser.close()
        receiver.shutdown()

    stop()
    start()
    status(selected)
    status(full)
    assert call(full,"aether_scripts_read",{"id":"cloud-note"})["source"]==edited
    print("Both grant modes survive restart: pass",flush=True)
    command("ais disconnect","revocation saved")
    for token in (selected,full):
        for _ in range(15):
            try:
                answer=http("/mcp",{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}},token)
                assert "error" in answer or answer.get("result",{}).get("isError"),"Revoked bearer remains valid"
                break
            except HTTPError as failure:
                assert failure.code in (401,403)
                assert "invalid_token" in failure.headers.get("WWW-Authenticate","")
                break
            time.sleep(1)
    print("Revoked tokens return invalid_token authentication challenge: pass",flush=True)
