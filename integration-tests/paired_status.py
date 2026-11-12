"""Drive the released command line and MCP protocol against a disposable Paper server."""
import json
import re
import subprocess
import time


class PairedClient:
    def __init__(self, repo, version, java, server):
        jar = repo / "gateway-mcp/build/libs" / f"gateway-mcp-{version}.jar"
        self.run_jar = [java, "-jar", str(jar)]
        self.details = server / "plugins/AetherScript/connection-details.json"
        self.profile = server / "test-client" / "profile.json"
        self.profile.parent.mkdir()
        self.version = version

    def pair(self, endpoint_line, code_line):
        endpoint = re.search(r"http://127\.0\.0\.1:\d+", endpoint_line).group()
        code = re.search(r": ([A-Za-z0-9_-]{43})$", code_line).group(1)
        command = [*self.run_jar, "pair", "--endpoint", endpoint, "--profile", str(self.profile)]
        result = subprocess.run(command, input=code + "\n", capture_output=True, text=True, timeout=20)
        assert result.returncode == 0, "Pair CLI failed on an unused code"
        assert code not in result.stdout + result.stderr
        assert self.profile.is_file()
        # Paper writes the connection instructions on its I/O worker.
        deadline = time.monotonic() + 5
        while not self.details.is_file() and time.monotonic() < deadline:
            time.sleep(0.05)
        details = json.loads(self.details.read_text(encoding="utf-8"))
        assert details == {"endpoint": endpoint, "scope": "status", "transport": "loopback-http"}

    def status(self):
        requests = [
            {"id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18",
                "capabilities": {}, "clientInfo": {"name": "paper-smoke", "version": "1"}}},
            {"method": "notifications/initialized"},
            {"id": 2, "method": "tools/call", "params": {"name": "aether_server_status", "arguments": {}}},
        ]
        frames = "".join(json.dumps({"jsonrpc": "2.0", **r}) + "\n" for r in requests)
        result = subprocess.run([*self.run_jar, "serve", "--profile", str(self.profile)],
                                input=frames, capture_output=True, text=True, check=True, timeout=20)
        responses = [json.loads(line) for line in result.stdout.splitlines()]
        assert len(responses) == 2 and responses[1]["id"] == 2
        return responses[1]["result"]

    def check_generation(self, generation):
        result = self.status()
        status = result["structuredContent"]
        assert not result["isError"] and status["connected"] and status["liveStateAvailable"]
        assert status["scope"] == "status" and status["deploymentEnabled"] is False
        server = status["server"]
        assert server["version"] == self.version and server["ready"]
        assert server["features"] == 1 and server["generation"] == generation

    def check_revoked(self):
        result = self.status()
        status = result["structuredContent"]
        assert result["isError"] and not status["connected"] and not status["liveStateAvailable"]
        assert not status["deploymentEnabled"] and "server" not in status
