"""Check discovery, one-line edits, precise errors and CLI/MCP agreement through the shipped JAR."""
import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--java", default="java")
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
version = re.search(r"^version=(.+)$", (repo / "gradle.properties").read_text(), re.M)[1]
jar = repo / "gateway-mcp/build/libs" / f"gateway-mcp-{version}.jar"

def run(arguments, source="", expected_exit=0):
    result = subprocess.run([args.java, *arguments], input=source.encode(), capture_output=True, timeout=30)
    assert result.returncode == expected_exit, result.stderr.decode(errors="replace")
    return result.stdout.decode()

def calls(entries):
    frames = [
        {"jsonrpc":"2.0", "id":1, "method":"initialize", "params":{"protocolVersion":"2025-06-18",
         "capabilities":{}, "clientInfo":{"name":"authoring-check","version":"1"}}},
        {"jsonrpc":"2.0","method":"notifications/initialized"}
    ]
    for number, (name, arguments) in enumerate(entries, 2):
        frames.append({"jsonrpc":"2.0","id":number,"method":"tools/call","params":{"name":name,"arguments":arguments}})
    output = run(["-jar",str(jar)], "".join(json.dumps(frame)+"\n" for frame in frames))
    return [json.loads(line)["result"]["structuredContent"] for line in output.splitlines()[1:]]

cli = ["-cp",str(jar),"dev.aetherscript.cli.Validate"]
guide = calls([("aether_capabilities",{})])[0]["authoring"]
assert json.loads(run([*cli,"--language"])) == guide
original = guide["template"]
edit = guide["editExample"]
assert original.count(edit["before"]) == 1
changed = original.replace(edit["before"], edit["after"])
broken = changed.replace("2 + 1", "true + 1")
assert broken != changed
comment = "// Document the greeting.\n" + changed
entries = [("aether_validate",{"format":"aether","source":source}) for source in [original,changed,broken,comment]]
first, second, error, commented = calls(entries)
assert first["valid"] and second["valid"] and commented["valid"]
assert first["featureId"] == second["featureId"] == "cloud-note"
assert first["irHash"] != second["irHash"]
assert second["irHash"] == commented["irHash"] and second["sourceHash"] != commented["sourceHash"]
assert error["valid"] is False and error["line"] == next(i for i,line in enumerate(broken.splitlines(),1) if "true + 1" in line)
assert error["column"] >= 1 and error["reason"]
assert json.loads(run([*cli,"aether"],changed))["irHash"] == second["irHash"]
cli_error = json.loads(run([*cli,"aether"],broken,expected_exit=1))
assert all(cli_error[key] == error[key] for key in ["line","column","reason","path"])
countdown = (repo / 'examples/countdown.aether').read_text(encoding='utf-8')
runtime_valid = calls([('aether_validate', {'format':'aether','source':countdown})])[0]
assert runtime_valid['valid']
runtime_tests = calls([('aether_test', {'format':'aether','source':countdown,
    'arguments':values,'expectedMessages':expected}) for values, expected in [
        ([], ['Count 3','Count 2','Count 1','Done']),
        (['2'], ['Count 2','Count 1','Done'])]])
assert all(test['matched'] for test in runtime_tests), runtime_tests
bad_control = countdown.replace('set remaining = remaining - 1', 'set remaining = true')
assert bad_control != countdown
bad_result = calls([('aether_validate', {'format':'aether','source':bad_control})])[0]
assert bad_result['valid'] is False and bad_result['line'] > 0
assert json.loads(run([*cli,'aether'],countdown))['irHash'] == runtime_valid['irHash']
print(json.dumps({"result":"pass","version":version,"checks":["discover template","validate template",
    "single-line edit changes behavior","comment preserves behavior","type error identifies line and column",
    "CLI and MCP agree on valid source","CLI and MCP agree on invalid source",
    "loop/function example validates through packaged CLI and MCP",
    "loop/function example runs with two argument lists", "invalid mutable assignment has source diagnostic"]},indent=2))
