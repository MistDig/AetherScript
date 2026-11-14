[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.11)

---

## The command desk

Use `/ais` in game, or `ais` in console. Script administration requires **`aetherscript.admin`**. Connection management requires the actual server console. Run checks the feature's own permission and sender rule.

| Command | Behavior |
| :--- | :--- |
| `ais connect [feature-ids]` | Server console. Open a browser approval link; optional comma-separated IDs constrain the browser choice. |
| `ais connections` | Server console. List connection IDs, names, permissions and revocations. |
| `ais disconnect [connection-id]` | Server console. Revoke one connection or all connections. Wait for saved confirmation. |
| `/ais status` | Version, readiness, generation, active count, busy state and AI status. |
| `/ais status <feature>` | Running revision, editable path, file state, errors and next action. |
| `/ais list` | Feature running/file states. |
| `/ais validate [feature]` | Check source and command collisions; no activation or saved version. |
| `/ais reload [feature]` | Activate and save valid changed source. |
| `/ais run <feature> [arguments…]` | Invoke with typed positional arguments and permission checks. |
| `/ais history <feature>` | Saved numbers, hashes, timestamps and active marker. |
| `/ais diff <feature>` | Running saved source versus current edit, even if invalid. |
| `/ais rollback <feature>` | Select the closest earlier readable, valid saved revision. |

### Direct script commands

An active feature registers its declared command name, such as `/helloai`, with the same permission, sender rule and argument parser as `/ais run`. A foreign command collision rejects activation and preserves the previous command. Renaming or retiring a feature removes its owned old labels. One command per feature is supported; aliases, subcommands and suggestions remain future work. Client command-tree refresh still needs connected-player evidence.

### One feature or the whole folder?

Targeted reload changes one feature. Full `/ais reload` validates all files and replaces the whole registry; one invalid included file rejects the batch. Removing a source and successfully doing a **full reload** retires the feature. Merely deleting a file before restarting can recover its saved copy.

Unchanged reloads report `UNCHANGED` and add no revision or generation. An explicit full reload releases rollback holds for included features.

| Response | Next step |
| :--- | :--- |
| `VALIDATED` | Reload when ready to activate. |
| `ACTIVE ... saved successfully` | Change activated and committed. |
| `UNCHANGED` | File already matches the working source. |
| `BUSY` | Wait for the current inspection/change, then retry. |
| `NOT_READY` | Check status and console for startup progress/errors. |
| `PERMISSION_DENIED` | Owner must grant the required admin node. |
| `Feature unavailable or permission denied` | Check readiness, ID, feature permission and sender rule. |
| `SAVE_FAILED` / `STORAGE_QUARANTINED` | Inspect storage health and recovery instructions. |

The actual server console can run `ais remote cloud-note` to grant scoped relay management of that feature, and `ais remote-off` to revoke it. `ais remote id1,id2` grants several IDs. Remote mode requires private `relay.json` configuration. The server console can also run `ais pair` to start a read-only status session and `ais unpair` to revoke it. Follow the [pairing guide](https://github.com/MistDig/AetherScript/wiki/Security-and-MCP).

Replies are bounded. Use per-feature commands or console for shortened lists. Hosted MCP supports permission-checked remote validation, source reading, creation/editing, activation and synthetic message tests. The `aether_scripts_apply` tool validates, activates, saves and reads back native source using an expected hash. Selected grants cover exact IDs; full access covers the supported native-script operations across valid feature IDs, including existing .aether files. General console commands, arbitrary filesystem access, Java/plugin installation and broader world operations are outside these grants. See the MCP reference and connection guide.

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.11
