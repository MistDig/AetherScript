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
scores = (repo/'examples/scores.aether').read_text(encoding='utf-8')
list_test = calls([('aether_test', {'format':'aether','source':scores,'expectedMessages':['6']})])[0]
assert list_test['matched'], list_test
bad_list = scores.replace('append(values, 3)', 'append(values, false)')
assert bad_list != scores
list_error = calls([('aether_validate', {'format':'aether','source':bad_list})])[0]
assert list_error['valid'] is False and list_error['line'] > 0
defaults = '''feature default-demo version "1"
fn multiply(value: integer, factor: integer = 2): integer { return value * factor }
command default-demo permission "example.defaults" sender any {
  send text(multiply(3))
  send text(multiply(3, 4))
}
'''
assert calls([('aether_test', {'format':'aether','source':defaults,'expectedMessages':['6','12']})])[0]['matched']
homes = (repo/'examples/homes.aether').read_text(encoding='utf-8')
home_valid, home_invalid, home_offline = calls([
    ('aether_validate', {'format':'aether','source':homes}),
    ('aether_validate', {'format':'aether','source':homes.replace('delay(20)', 'delay(true)')}),
    ('aether_test', {'format':'aether','source':homes.replace('sender player', 'sender any'), 'expectedMessages':[]})])
assert home_valid['valid'] and set(home_valid['requiredCapabilities']) == {
    'runtime.message.send','player.location.read','state.own','scheduler.delay','player.teleport.self'}, home_valid
assert not home_invalid['valid'] and home_invalid['line'] > 0, home_invalid
assert not home_offline['valid'] and 'HOST_CONTEXT_REQUIRED' in home_offline['message'], home_offline
assert {c['name'] for c in guide['hostCallables']} >= {
    'player_location','state_get_location','state_put_location','delay','teleport_self'}
catalog = {call['name']: call for call in guide['hostCallables']}
for name, result_type in [('state_save', 'unit'), ('state_load', 'T')]:
    assert catalog[name]['parameters'] == ['text', 'T'] and catalog[name]['returns'] == result_type
    assert catalog[name]['requiredCapabilities'] == ['state.own']
progress = guide['typedPlayerStorage']['example']
assert progress == (repo / 'examples/player-progress.aether').read_text(encoding='utf-8')
bad_progress = progress.replace('state_save("progress",', 'state_save(123,')
progress_valid, progress_error = calls([('aether_validate', {'format':'aether','source':source})
                                        for source in (progress, bad_progress)])
assert progress_valid['valid'] and set(progress_valid['requiredCapabilities']) == {'state.own','runtime.message.send'}, progress_valid
assert not progress_error['valid'] and progress_error['reason'] and progress_error['line'] == next(
    number for number, line in enumerate(bad_progress.splitlines(), 1) if 'state_save(123,' in line)
events = guide['events']
protection = events['example']
assert protection == (repo / 'examples/starter-protection.aether').read_text(encoding='utf-8')
event_valid, event_invalid = calls([('aether_validate', {'format':'aether','source':source})
                                   for source in (protection, protection.replace('on block_break', 'on join', 1))])
assert event_valid['valid'] and set(event_valid['requiredCapabilities']) == {
    'runtime.message.send', 'runtime.event.block_break', 'runtime.event.block_place', 'runtime.event.cancel'}
assert not event_invalid['valid'] and event_invalid['line'] > 0 and 'cancel' in event_invalid['reason']
print(json.dumps({"result":"pass","version":version,"checks":["discover template","validate template",
    "single-line edit changes behavior","comment preserves behavior","type error identifies line and column",
    "CLI and MCP agree on valid source","CLI and MCP agree on invalid source",
    "loop/function example validates through packaged CLI and MCP",
    "loop/function example runs with two argument lists", "invalid mutable assignment has source diagnostic",
    "list iteration example emits expected total", "mixed append rejects with source diagnostic",
    "function defaults and explicit overrides run through packaged MCP",
    "homes validates with inferred capabilities", "host argument types reject with location",
    "offline native execution requires host context", "host discovery exports executable callable catalog",
    "typed state signatures discovered", "player-progress validates with scoped capabilities",
    "starter protection validates with local event capabilities", "unsupported cancellation identifies source location"]},indent=2))
