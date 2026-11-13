"""Read-only hosted MCP discovery using the installed Codex app-server client."""
import json
import os
import queue
import shutil
import subprocess
import threading
import time


def discover(endpoint, token):
    executable = shutil.which("codex")
    if executable is None:
        raise RuntimeError("Codex executable required for this opt-in client check")
    environment = os.environ.copy()
    environment["AETHERSCRIPT_CLIENT_TEST_TOKEN"] = token
    name = "aetherscript_release_test"
    configuration = {
        "mcp_servers": {},
    }
    arguments = [executable, "app-server", "-c", "mcp_servers={}"]
    for key, value in {"url": endpoint, "bearer_token_env_var": "AETHERSCRIPT_CLIENT_TEST_TOKEN"}.items():
        arguments += ["-c", f"mcp_servers.{name}.{key}=" + json.dumps(value)]
    process = subprocess.Popen(arguments, env=environment, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, text=True, encoding="utf8", errors="replace")
    incoming = queue.Queue()

    def receive():
        for line in process.stdout:
            try:
                incoming.put(json.loads(line))
            except json.JSONDecodeError:
                pass
    threading.Thread(target=receive, daemon=True).start()

    def request(number, method, params):
        process.stdin.write(json.dumps({"id": number, "method": method, "params": params}) + "\n")
        process.stdin.flush()
        deadline = time.monotonic() + 40
        while time.monotonic() < deadline:
            message = incoming.get(timeout=max(.1, deadline-time.monotonic()))
            if message.get("id") == number:
                assert "error" not in message, "Codex client request failed"
                return message["result"]
        raise TimeoutError("Codex client discovery timed out")
    try:
        request(1, "initialize", {"clientInfo": {"name": "aetherscript_client_check", "version": "1"},
                "capabilities": {"experimentalApi": True}})
        process.stdin.write('{"method":"initialized","params":{}}\n')
        process.stdin.flush()
        result = request(2, "mcpServerStatus/list", {"serverName": name})
        row = next(item for item in result["data"] if item["name"] == name)
        expected = {"aether_server_status", "aether_scripts_list", "aether_scripts_read",
                    "aether_scripts_validate", "aether_scripts_apply", "aether_scripts_test"}
        assert expected == set(row["tools"]), "Codex did not discover all hosted tools"
        print("Installed Codex discovered six hosted MCP tools: pass", flush=True)
    finally:
        process.stdin.close()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait()
