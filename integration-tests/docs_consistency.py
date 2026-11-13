"""Catch contradictions between current documentation and shipped capability surfaces."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
current = [root/"README.md", root/"docs/roadmap.md", root/"docs/architecture.md",
           root/"docs/ai-reference.md", *[p for p in (root/"docs/wiki").glob("*.md") if p.name != "Testing-0.0.2.md"]]
stale = [
 r"No deploy command or remote mutation endpoint exists",
 r"Current 0[.]0[.]4 results",
 r"Secure remote connectivity [(]gated, not enabled[)]",
 r"This experimental release supports sender-only message actions and preparation-time text-script logic",
 r"For scoped remote authoring, the owner first configures a personal HTTPS relay",
 r"Now [·] 0[.]0[.]2",
]
for path in current:
 text=path.read_text(encoding="utf8")
 for pattern in stale:
  assert not re.search(pattern,text,re.I), f"{path.relative_to(root)}: stale claim {pattern}"
readme=(root/"README.md").read_text(encoding="utf8")
assert "invocation-time" in readme and "preparation-time" in readme
commands=(root/"docs/wiki/Commands.md").read_text(encoding="utf8")
assert "aether_scripts_apply" in commands and "full access" in commands.lower()
reference=(root/"docs/ai-reference.md").read_text(encoding="utf8")
assert "Hosted clients cannot call aether_capabilities" in reference
assert "allScripts" in reference
implementation=(root/"plugin-paper/src/main/java/dev/aetherscript/paper/RemoteScripts.java").read_text()
for op in ("scripts.read","scripts.validate","scripts.apply","scripts.test"):
 assert op in implementation
assert "feature.program().evaluate" in (root/"core/src/main/java/dev/aetherscript/core/Registry.java").read_text()
full=(root/"site/public/llms-full.txt").read_text(encoding="utf8")
assert reference.strip() in full
assert full.index(reference.strip()) < full.index("# Human wiki appendix")
for path in (root/"docs/evidence/0.0.9").glob("TEST-*.xml"):
 assert 'failures="0"' in path.read_text(encoding="utf8")
print("Documentation consistency checks passed")
