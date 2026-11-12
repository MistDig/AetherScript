"""Prepare immutable release assets from a built checkout and a matching version tag."""
import argparse
import hashlib
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--tag", required=True)
args = parser.parse_args()
properties = (root / "gradle.properties").read_text(encoding="utf-8")
match = re.search(r"^version=([0-9]+\.[0-9]+\.[0-9]+)$", properties, re.M)
if not match or args.tag != "v" + match.group(1):
    parser.error("tag must match the version in gradle.properties")
version = match.group(1)
changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
section = re.search(r"^## \[" + re.escape(version) + r"\] - \d{4}-\d{2}-\d{2}\n(.*?)(?=^## |^\[[^\]]+\]:|\Z)", changelog, re.M | re.S)
if not section:
    parser.error("missing dated changelog section for version")
out = root / "build" / "release"
out.mkdir(parents=True, exist_ok=True)
assets = []
for module, name in [("plugin-paper", f"AetherScript-{version}.jar"), ("gateway-mcp", f"AetherScript-MCP-{version}.jar")]:
    source = root / module / "build" / "libs" / f"{module}-{version}.jar"
    if not source.is_file():
        parser.error(f"missing built artifact: {source}; run clean check build")
    destination = out / name
    shutil.copy2(source, destination)
    assets.append(destination)
windows_setup = out / f"AetherScript-Codex-Windows-{version}.zip"
with zipfile.ZipFile(windows_setup, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
    script = (root / "scripts/Connect-AetherScript.cmd").read_text(encoding="ascii")
    bundle.writestr("Connect-AetherScript.cmd", script.replace("RELEASE_VERSION", version).replace("\n", "\r\n"))
    bundle.write(assets[1], assets[1].name)
assets.append(windows_setup)
archive = out / f"AetherScript-{version}-source.zip"
subprocess.run(["git", "archive", "--format=zip", "--prefix=AetherScript/", f"--output={archive}", "HEAD"], cwd=root, check=True)
assets.append(archive)
(out / "SHA256SUMS.txt").write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n" for p in assets), encoding="utf-8", newline="\n")
(out / "NOTES.md").write_text(section.group(1).strip() + "\n", encoding="utf-8", newline="\n")
print(f"Prepared {args.tag}: {out}")
