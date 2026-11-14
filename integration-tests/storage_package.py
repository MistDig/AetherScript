"""Check actual JDBC/native packaging and licenses in the shipped shaded JARs."""
import argparse
import json
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--java', default='java')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
version = re.search(r'^version=(.+)$', (repo/'gradle.properties').read_text(), re.M)[1]
results = []
for module in ['gateway-mcp', 'plugin-paper']:
    jar = repo/module/'build/libs'/f'{module}-{version}.jar'
    with zipfile.ZipFile(jar) as archive:
        assert 'org/sqlite/JDBC.class' in archive.namelist()
        assert 'META-INF/maven/org.xerial/sqlite-jdbc/LICENSE' in archive.namelist()
        assert 'META-INF/maven/org.xerial/sqlite-jdbc/LICENSE.zentus' in archive.namelist()
    with tempfile.TemporaryDirectory(prefix='aether-package-') as directory:
        result = subprocess.run([args.java, '-cp', str(jar),
            str(repo/'integration-tests/fixtures/StoragePackageCheck.java'), directory],
            capture_output=True, text=True, timeout=45)
        assert result.returncode == 0, result.stderr
        assert 'PACKAGED_SQLITE_OK' in result.stdout, result.stdout
    results.append(dict(jar=jar.name, result='pass'))
print(json.dumps(dict(result='pass', version=version, packages=results), indent=2))
