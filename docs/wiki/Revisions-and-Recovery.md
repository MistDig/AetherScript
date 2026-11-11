[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.4)

---

## One file to edit. A safety net behind it.

Edit `plugins/AetherScript/scripts/<feature>.yml` (or `.aether`, `.yaml`, `.json`). The engine manages internal working copies under `storage/`; do not edit those copies.

### What is saved?

Successfully activated **changed source** creates a numbered revision. Invalid edits, validation-only commands and unchanged reloads consume no history. Comment/whitespace byte changes count as changes. The script's `version` label does not control revision numbers.

Default: **five saved revisions per feature, including the active one**. The oldest non-active revision is removed when the limit is exceeded. Numbers keep increasing. Repeated content can share one stored blob across historical revisions.

### On startup

| Situation | Result |
| :--- | :--- |
| Valid editable source; no rollback hold | Activate it, saving a revision if changed. |
| Invalid/missing file with usable saved source | Run saved source and explain the edit target. |
| Invalid new feature, no history | Disable that feature; unrelated valid sources can load. |
| Corrupt active bytes and unusable editable file | Try earlier committed, checksum-valid, supported revisions. |
| No usable saved copy | Disable the affected feature and report it. |
| Corrupt manifest | Block engine startup; never guess from loose blobs. |
| Rollback held | Restore the selected revision until explicit reload. |

Recovered scripts are parsed and validated against the current policy. Startup I/O runs on the worker, but readiness waits for preparation. Recovery does not activate every historical revision; corrupt copies may require checking earlier ones. No instant-start or large-server performance claim is made.

### Roll back without losing edits

```text
/ais history hello-ai
/ais rollback hello-ai
/ais status hello-ai
/ais diff hello-ai
```

Rollback selects the closest earlier readable, valid revision. Repeating walks backward through retained history. It creates no revision and **does not overwrite the editable file**.

The selected rollback persists across restarts. Fix the editable file and run `/ais reload hello-ai` to opt back in. A successful full reload also releases holds for included features. Rollback cannot undo messages already sent or world/economy effects.

### Configuration

`plugins/AetherScript/config.yml`:

```yaml
configVersion: 1
retainVersions: 5
```

Allowed retention: **1–50**, applied on restart. A limit of 1 leaves no earlier revision. Lowering it protects the active version, even an older rollback. Unknown/duplicate keys and anchors are rejected.

### Interrupted saves and storage failures

Source bytes are staged before a checksummed manifest is atomically replaced. Only manifest-referenced revisions are committed. Pending files and orphan blobs are not selected at startup. Abrupt-process-exit tests cover boundaries before replacement and after commit.

This is not a power-loss durability guarantee: filesystem directory flushing varies, particularly on Windows. Atomic moves are required; the store does not silently substitute a non-atomic replacement.

If saving or the completion audit fails after activation, the engine attempts to restore the old durable state and registry. Registry restoration uses a new generation. If restoration is uncertain, operations are quarantined. Already dispatched effects cannot be undone.

> [!IMPORTANT]
> Back up the whole `plugins/AetherScript/` directory with Paper stopped. For `STORAGE_CORRUPT` or `STORAGE_QUARANTINED`, stop Paper, preserve the damaged directory for diagnosis and restore a known-good owner backup. Do not randomly delete manifests or edit hashes. Revision storage complements backups; it does not replace them.

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.4
