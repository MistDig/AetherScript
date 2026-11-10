[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.2)

---

## A deliberately small language surface

Strict **YAML and JSON** compile to the same typed engine representation. A human-friendly DSL is planned on top of this foundation.

Use one file per feature: `scripts/<id>.yml`, `.yaml` or `.json`. Its filename and `id` must match. Do not install both formats for the same ID.

```yaml
schemaVersion: 1
id: hello-ai
version: "1.0.0"
requires:
  - runtime.message.send
commands:
  - name: helloai
    permission: aetherscript.example.hello
    sender: any
    actions:
      - type: message.send
        target: sender
        text: "Hello from AetherScript!"
```

| Field | Accepted meaning |
| :--- | :--- |
| `schemaVersion` | Integer `1`. |
| `id` | 1–64 characters: lowercase letter first, then lowercase letters, digits or hyphens. Matches filename. |
| `version` | Nonempty string up to 64 characters; your label, independent of revision numbering. |
| `requires` | Exactly `[runtime.message.send]`. |
| `commands` | Exactly one command. |
| `name` | Same character rules as ID; unique among active features. Reserved metadata, not an alias. |
| `permission` | Explicit dotted permission node, up to 128 characters; no wildcard. |
| `sender` | `any` or `player`; the latter rejects console invocation. |
| `actions` | 1–32 message actions. |
| `type` | Only `message.send`. |
| `target` | Only `sender`, meaning the invoker. `all` and `everyone` are not implemented. |
| `text` | Plain nonempty string up to 2,048 UTF-16 units; no controls or legacy section-sign formatting. |

Unknown fields/actions, duplicate keys, multiple documents, YAML tags, anchors and aliases are rejected. Quote string values. JSON requires quoted keys and valid commas, without comments.

Limits: 64 KiB source, nesting depth 32, 128 active features, 128 tracked histories, and 256 scripts-directory entries. Files must be regular files inside the scripts directory.

No variables, interpolation, expressions, loops, event handlers or arbitrary Java/console execution exist yet. `{player}` is literal text.

### Make a second example

Copy the YAML above to `scripts/welcome.yml`, change `id` and `name` to `welcome`, permission to `aetherscript.example.welcome`, and text to your welcome message. Validate and reload `welcome`, then invoke `/ais run welcome`. This is manually invoked; it is not a join-event script.

[Working YAML and JSON examples](https://github.com/MistDig/AetherScript/tree/v0.0.2/examples) · [JSON Schema](https://github.com/MistDig/AetherScript/blob/v0.0.2/schema/feature-v1.schema.json)

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.2
