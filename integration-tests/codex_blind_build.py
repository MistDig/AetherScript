"""Opt-in check with an installed Codex CLI and a disposable server's scoped MCP grant."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def blind_build(endpoint, token, server, passed):
    codex = shutil.which('codex')
    if not codex:
        raise RuntimeError('Installed Codex CLI required for the opt-in client check')
    version = subprocess.run([codex, '--version'], capture_output=True, text=True, timeout=15, check=True).stdout.strip()
    environment = os.environ.copy()
    environment['AETHERSCRIPT_CLIENT_TEST_TOKEN'] = token
    name = 'aetherscript_release_test'
    prompt = '''On the connected disposable Minecraft server, build a visitor desk feature with the ID visitor-desk. The command /visitordesk takes a name and a boolean staff flag. With MistDig true the only message is "Staff desk for MistDig." With Alex false the only message is "Visitor desk for Alex." When a player joins, send them "Welcome to the visitor desk." Use the AetherScript MCP connection only. Discover the installed guide without local files or web docs, validate and activate the feature, then use the available tests to check both command results and the join behavior. Do not change or invoke other scripts.'''
    with tempfile.TemporaryDirectory(dir=server, prefix='codex-client-') as empty:
        arguments = [codex, '-a', 'never', 'exec', '--ephemeral', '--json', '--skip-git-repo-check',
                     '--sandbox', 'read-only', '-C', empty, '-c', 'mcp_servers={}',
                     '-c', f'mcp_servers.{name}.url=' + json.dumps(endpoint),
                     '-c', f'mcp_servers.{name}.bearer_token_env_var="AETHERSCRIPT_CLIENT_TEST_TOKEN"',
                     '-c', f'mcp_servers.{name}.required=true',
                     '-c', f'mcp_servers.{name}.tool_timeout_sec=75']
        for tool in ['aether_scripts_apply','aether_scripts_test']:
            arguments += ['-c', f'mcp_servers.{name}.tools.{tool}.approval_mode="approve"']
        arguments.append(prompt)
        result = subprocess.run(arguments, env=environment, capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=360)
    calls = []
    for line in result.stdout.splitlines():
        try:
            row = json.loads(line)
        except ValueError:
            continue
        item = row.get('item') or {}
        if row.get('type') == 'item.completed' and item.get('type') == 'mcp_tool_call' and item.get('server') == name:
            error = '(error details withheld)'
            classification = json.dumps(item.get('error') or item.get('result') or {}).lower()
            if 'approval' in classification: error = 'approval'
            elif 'timeout' in classification or 'timed out' in classification: error = 'transport timeout'
            elif 'denied' in classification: error = 'denied'
            calls.append({'tool':item.get('tool'),'status':item.get('status'),'classification':error if item.get('status')!='completed' else None})
    names = [call['tool'] for call in calls]
    (server/'codex-client-tools.json').write_text(json.dumps({'version':version,'exitCode':result.returncode,
        'toolNames':names,'calls':calls}, indent=2), encoding='utf-8')
    required = {'aether_capabilities','aether_docs_read','aether_scripts_validate','aether_scripts_apply','aether_scripts_test','aether_events_preview'}
    if result.returncode or not required.issubset(names):
        raise AssertionError('Installed Codex did not finish the blind workflow; inspect the sanitized Codex tool trace')
    passed('actual-codex-discovery', version + ' discovered the connected guide from an empty directory, applied its own script, and called command tests and event preview.')
