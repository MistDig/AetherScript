"""Validate every complete native/structured script example in the human wiki with the built compiler."""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--java", default="java")
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
version = re.search(r"^version=(.+)$", (repo / "gradle.properties").read_text(), re.M)[1].strip()
jar = repo / "gateway-mcp/build/libs" / f"gateway-mcp-{version}.jar"
checked = 0
for page in sorted((repo / "docs/wiki").glob("*.md")):
    for match in re.finditer(r"^```([^\n]*)\n(.*?)^```\s*$", page.read_text(encoding="utf8"), re.M | re.S):
        language, source = match[1].strip(), match[2]
        invalid = language.endswith("-invalid")
        format_name = language.removesuffix("-invalid")
        if source.lstrip().startswith("feature "):
            format_name = "aether"
        elif format_name not in ("yaml", "json") or not re.search(r'(?:^|["\s])schemaVersion["\s]*:', source, re.M):
            continue
        result = subprocess.run([args.java, "-cp", str(jar), "dev.aetherscript.cli.Validate", format_name],
                                input=source, text=True, capture_output=True, timeout=15)
        valid = result.returncode == 0
        assert valid != invalid, f"{page.name}: unexpected validation result {result.stdout[:300]}"
        response = json.loads(result.stdout)
        if invalid:
            assert response.get("line", 0) >= 1 and response.get("reason")
        checked += 1
assert checked > 0
print(json.dumps({"result": "pass", "wikiExamplesChecked": checked, "version": version}))
