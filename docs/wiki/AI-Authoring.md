[Documentation home](https://mistdig.github.io/AetherScript/)

---

## Write a feature

Save this as `scripts/cloud-note.aether`. The feature ID matches the filename. A server administrator can validate and activate it with `/ais validate cloud-note` and `/ais reload cloud-note`. An authorized sender invokes it with `/ais run cloud-note`.

```aether
feature cloud-note version "1.0.0"
command cloud-note permission "aetherscript.example.cloud" sender any {
  let message = "Welcome to the clouds."
  let stars = 2 + 1
  if stars >= 3 {
    send message
  } else {
    send "Keep exploring."
  }
}
```

To change the greeting, edit the quoted value on the `let message` line. Leave the feature ID, command permission, and sender rule in place. Save the complete file and validate it before reloading. If validation fails, correct the reported line in that file. The previous working revision continues serving players.

## Grammar and evaluation

Use a feature header, a command header, then one statement per line. Braces delimit blocks. `let` introduces an immutable local binding with an inferred type. Values can be text, signed 64-bit integers or booleans. `if` expects a boolean. `send` expects text. `text(value)` converts a value explicitly. Comments begin with `//`.

The engine checks names and types in both branches. Commands without arguments prepare their messages on reload. Commands declaring typed arguments evaluate their messages and conditions on each invocation, after checking argument values. Invocation checks the command permission and sender rule. Command arguments in 0.0.7 enable invocation-time values and conditions. See the language reference for declarations and quoting. Player data, functions and persistence remain planned.

## Get machine-readable instructions

Call `aether_capabilities` in the offline MCP prototype and read the `authoring` object. It contains the syntax version, editable template, value types, operations and a before/after edit. Pass the entire edited source to `aether_validate` with `format: "aether"`. On failure read `line`, `column`, `path` and `reason`, correct the reported expression, then submit the complete source again.

The site also publishes [plain-text authoring instructions](https://mistdig.github.io/AetherScript/llms.txt) and the [JSON authoring contract](https://mistdig.github.io/AetherScript/authoring-v1.json). The CLI and MCP load the same contract bundled in the released JAR.

## Validate from a terminal

With Java 25 and the 0.0.7 MCP JAR, print the language contract:

```sh
java -cp AetherScript-MCP-0.0.9.jar dev.aetherscript.cli.Validate --language
```

Validate a file through standard input on a POSIX shell:

```sh
java -cp AetherScript-MCP-0.0.9.jar dev.aetherscript.cli.Validate aether < cloud-note.aether
```

In PowerShell, pipe UTF-8 text into the command:

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
Get-Content -Raw -Encoding utf8 cloud-note.aether | java -cp AetherScript-MCP-0.0.9.jar dev.aetherscript.cli.Validate aether
```

The CLI writes one JSON result. Exit code 0 means valid, 1 means validation failed, and 2 means the CLI arguments need correction. Use `yaml` or `json` to validate an existing structured definition. Validation reads the input and leaves deployment under the server administrator's control.

---

[Back to the field guide](https://mistdig.github.io/AetherScript/)

Use Copy docs link for AI on the documentation website to share the [complete release guide](https://mistdig.github.io/AetherScript/releases/0.0.9/llms-full.txt). Download it as an attachment when the AI cannot open links.

## Runtime argument tests

Declare `arg name: text` at the start of a command and use that name in messages and conditions. Supply `arguments: ["MistDig"]` when testing through MCP. Missing required arguments and invalid boolean/integer values should produce an error before any messages are sent. Each invocation gets fresh local values.
