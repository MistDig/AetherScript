"""Exercise the shipped gateway and Paper plugin through an operator-provided Cloudflare relay."""
import json
import re
import subprocess


def test_remote_scripts(repo, version, java, server, console):
    feature = "cloud-smoke"
    permission = "aetherscript.example.cloudsmoke"
    source = (f'feature {feature} version "1.0.0"\n'
              f'command cloudsmoke permission "{permission}" sender any {{\n'
              '  send "Created by MCP"\n}\n')
    run = [java, "-jar", str(repo / "gateway-mcp/build/libs" / f"gateway-mcp-{version}.jar")]
    profile = server / "test-cloud-client" / "profile.json"
    uri_line = console("ais remote " + feature, "aether://pair?")
    uri = re.search(r"aether://pair\?\S+", uri_line).group()
    paired = subprocess.run([*run, "pair", "--profile", str(profile)], input=uri + "\n",
                            text=True, capture_output=True, timeout=40)
    assert paired.returncode == 0, "Cloud pairing failed"
    assert uri not in paired.stdout + paired.stderr

    def call(tool, arguments, *, success=True):
        messages = [
            {"id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18",
             "capabilities": {}, "clientInfo": {"name": "cloud-paper-test", "version": "1"}}},
            {"method": "notifications/initialized"},
            {"id": 2, "method": "tools/call", "params": {"name": tool, "arguments": arguments}},
        ]
        frames = "".join(json.dumps({"jsonrpc": "2.0", **message}) + "\n" for message in messages)
        result = subprocess.run([*run, "serve", "--profile", str(profile)], input=frames, text=True,
                                capture_output=True, timeout=90, check=True)
        reply = json.loads(result.stdout.splitlines()[-1])
        assert reply["id"] == 2
        response = reply.get("result", {})
        assert (not response.get("isError", True)) == success, f"{tool} returned unexpected success={not response.get('isError', True)}"
        return response.get("structuredContent", {})

    try:
        assert feature in call("aether_scripts_list", {})["ids"]
        assert not call("aether_scripts_read", {"id": feature})["exists"]
        assert call("aether_scripts_validate", {"id": feature, "source": source})["valid"]
        put = {"id": feature, "source": source, "expectedHash": "ABSENT"}
        created = call("aether_scripts_apply", put)
        assert created["hash"] == created["activeHash"] and created["source"] == source
        assert call("aether_scripts_test", {"id": feature, "expectedMessages": ["Created by MCP"]})["matched"]
        edited = source.replace("Created by MCP", "Edited by MCP")
        updated = call("aether_scripts_apply", dict(put, source=edited, expectedHash=created["hash"]))
        call("aether_scripts_apply", dict(put, expectedHash=created["hash"]), success=False)
        call("aether_scripts_apply", dict(put, source="invalid", expectedHash=updated["hash"]), success=False)
        call("aether_scripts_read", {"id": "hello-ai"}, success=False)
        assert call("aether_scripts_test", {"id": feature, "expectedMessages": ["Edited by MCP"]})["matched"]
        assert call("aether_scripts_read", {"id": feature})["source"] == edited
        console("ais run " + feature, "Edited by MCP")
    finally:
        console("ais remote-off", "revoked")
    call("aether_scripts_list", {}, success=False)
