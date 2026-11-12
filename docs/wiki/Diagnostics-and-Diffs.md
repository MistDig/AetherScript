[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.5)

---

## Know what is wrong — and what still runs

An invalid target produces a diagnostic shaped like this; actual line/column depend on your file:

```text
Edit scripts/hello-ai.yml: line 12, column 17; $.commands[0].actions[0].target
Found "everyone". Allowed: sender. Fix: set target to sender; only the invoking sender is permitted
Fix that editable file, then run /ais reload hello-ai.
Rejected; active generation unchanged. Last working scripts remain active.
```

Syntax errors point to the parser location when available. Size/path errors can report `location unavailable`.

### Two states, one edit target

Run `/ais status hello-ai`. It identifies the running saved revision separately from the editable file:

| Label | Meaning |
| :--- | :--- |
| `UP TO DATE` | Editable bytes match running source. |
| `RUNNING SAVED VERSION - file has errors` | Invalid edit; keep fixing the same file. |
| `RUNNING SAVED VERSION - file has unapplied changes` | Valid edit differs from running source. |
| `RUNNING SAVED VERSION - editable file missing` | Restore an editable source while the saved copy runs. |
| `NOT ACTIVE - valid file; reload to activate` | Valid source is not currently active. |
| `DISABLED - file has errors` | No working feature is running for that invalid file. |

### Compare before reloading

`/ais diff hello-ai` gives a compact comparison:

```diff
--- running saved revision #2
+++ scripts/hello-ai.yml (editable, not active)
@@ old line 12, editable line 12 @@
- 12:         target: sender
+ 12:         target: everyone
```

Minus lines are running source; plus lines are your edit. Safely readable invalid files can still be compared. Long changes/lines are shortened for chat; byte-only encoding or line-ending changes get an explanation. This is a comparison, not a patch tool.

Fix the shown file, validate it, reload it, then run the feature. Never edit internal saved copies. If rollback is held, status explains that explicit reload is needed.

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.5
