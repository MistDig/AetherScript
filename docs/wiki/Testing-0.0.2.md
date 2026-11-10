[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.2)

---

## Your 0.0.2 test flight

Use a test server first. Stop Paper, back up `plugins/AetherScript/`, replace the old JAR and keep only one plugin JAR installed. Do not corrupt live-server storage for testing.

### Start and save

- [ ] `/ais status` reports `0.0.2`, `ready=true`, `AI deployment=ABSENT`.
- [ ] `/ais run hello-ai` works without an initial reload.
- [ ] Change the editable text to `Version B`; reload and run it.
- [ ] History shows the new revision. Reload unchanged: history does not grow.

### Break an edit, then restart

- [ ] Change `target: sender` to `target: everyone`; reload is rejected.
- [ ] The error names the file, location, field and correction.
- [ ] Run still returns `Version B`; status says the file has errors.
- [ ] Diff shows the running target and invalid edit.
- [ ] Restart with that invalid file: `Version B` still runs.
- [ ] Fix the target, change the text to `Version C`, then reload.

### Roll back and check access

- [ ] Rollback restores an earlier message without overwriting editable `Version C`.
- [ ] Restart: rollback stays selected. Explicit reload restores `Version C`.
- [ ] Non-admin cannot reload, inspect history/diff or roll back.
- [ ] A player without the feature's permission cannot run it.
- [ ] More than five distinct successful edits retain five versions by default, including active.

If a check fails, record version, Paper build, command, diagnostic and expected behavior. Share only relevant script/log excerpts with private data removed.

### Automated evidence

**70 automated tests passed:** 63 core cases and 7 MCP protocol cases, including retention, corruption, interrupted saves and abrupt child-JVM exits. The packaged MCP subprocess passed. A real disposable Paper 26.3-245 server passed **28 checks across five boots and 100 distinct live reloads**, including invalid-edit restart recovery, held rollback and corrupt storage.

[Versioned evidence](https://github.com/MistDig/AetherScript/tree/v0.0.2/docs/evidence/0.0.2) · [Test notes](https://github.com/MistDig/AetherScript/blob/v0.0.2/docs/testing.md)

Console automation is not connected-player automation. Earlier 0.0.1 player tests were user-reported; please repeat these checks on 0.0.2. Purpur, named MCP clients and performance benchmarks remain unverified.

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.2
