# Feature schema v1 / engine v0.1

`schema/feature-v1.schema.json` describes the structural contract. Runtime validation also enforces byte/depth limits, plain-text restrictions, duplicate-key rejection, filename/ID consistency, capability inference, and registry collisions. Both YAML and JSON compile to the same `Feature` record. YAML string-like values with numeric/boolean/tag interpretation must be quoted.

Required top-level fields: `schemaVersion: 1`, lowercase logical `id`, string `version`, exactly `[runtime.message.send]` in `requires`, and exactly one `commands` entry. A command needs a lowercase logical `name`, an explicit non-wildcard `permission`, `sender: any|player`, and 1–32 actions. An action is exactly `type: message.send`, `target: sender`, and bounded plain `text`.

Declared command names are collision-checked metadata, not native aliases. Invoke with `/ais run <id>`. Source hashes preserve original bytes; IR hashes use deterministic Jackson serialization of immutable records. Versions are labels, not compatibility promises.
