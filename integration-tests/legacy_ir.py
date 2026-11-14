"""Compare current packaged compilation with a corpus recorded from published 0.0.11."""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--java', default='java')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
version = re.search(r'^version=(.+)$', (repo/'gradle.properties').read_text(), re.M)[1]
jar = repo/'gateway-mcp/build/libs'/f'gateway-mcp-{version}.jar'
fixture = json.loads((repo/'integration-tests/fixtures/legacy-ir-v011.json').read_text(encoding='utf-8'))
for case in fixture['cases']:
    result = subprocess.run([args.java, '-cp', str(jar), 'dev.aetherscript.cli.Validate', case['format']],
        input=case['source'], capture_output=True, text=True, timeout=30)
    assert result.returncode == 0, (case['name'], result.stderr, result.stdout)
    assert json.loads(result.stdout)['irHash'] == case['irHash'], case['name']
print(json.dumps(dict(result='pass', version=version, baseline=fixture['baselineRelease'],
    semanticHashesChecked=len(fixture['cases'])), indent=2))
