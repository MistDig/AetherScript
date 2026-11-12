<p align="center"><img src="docs/assets/aetherscript-banner.png" alt="AetherScript — little scripts, big possibilities" width="1200"></p>

<p align="center"><a href="https://github.com/MistDig/AetherScript/releases/tag/v0.0.5"><b>Download 0.0.5</b></a> · <a href="https://mistdig.github.io/AetherScript/">Explore the docs</a> · <a href="CHANGELOG.md">Changelog</a> · <a href="docs/roadmap.md">Roadmap</a></p>

[![Build](https://github.com/MistDig/AetherScript/actions/workflows/ci.yml/badge.svg)](https://github.com/MistDig/AetherScript/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/MistDig/AetherScript)](https://github.com/MistDig/AetherScript/releases)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

An open-source Minecraft scripting engine with **live hot reloading**, saved working revisions and a path toward secure, vendor-neutral AI tooling. Target: **Paper 26.3 build 245 · Java 25**.

## Read-only server pairing in 0.0.5

The MCP gateway can read an owner-paired server's status through an authenticated loopback bridge. The server console issues a five-minute single-use code, and the gateway stores a one-hour `status` credential in an owner-restricted profile. The [pairing guide](https://mistdig.github.io/AetherScript/guide/security.html) includes local setup, revocation and SSH tunnel instructions.

## Text scripts introduced in 0.0.4

The first `.aether` frontend supports immutable local values, checked integer arithmetic, text and boolean expressions, `if`/`else`, explicit `text(...)` conversion and source-located diagnostics. Expressions evaluate during validation/reload and compile into the existing permission-checked message actions.

```text
feature hello-ai version "1.0.0"
command helloai permission "aetherscript.example.hello" sender any {
  let greeting = "Hello"
  let count = 2 + 3
  if count == 5 {
    send greeting + " from AetherScript!"
  } else {
    send "Unexpected count"
  }
}
```

Save this as `scripts/hello-ai.aether`, using one source file for that feature ID. Validate, reload and run it with the existing `/ais` commands. The [language guide](https://mistdig.github.io/AetherScript/guide/language.html) covers types, scope, operators and preparation limits.

The [documentation website](https://mistdig.github.io/AetherScript/) includes local search, sidebar guides, dark mode and mobile navigation. The [ecosystem inventory](docs/ecosystem/README.md) tracks the brief's concrete capabilities, evidence and dependency order. Full runtime variables, functions, loops, real command registration, persistent gameplay state and addon APIs remain staged work in that inventory.

## What the engine does

- YAML, JSON and `.aether` sources compile to the shared immutable message model.
- Live reload with invalid edits preserving the running script.
- Saved successful revisions, startup recovery and local rollback; five versions per feature by default.
- Clear editable-file versus running-version status, field/line/column errors, history and bounded diffs.
- A separate **read-only MCP** gateway with offline authoring/validation and opt-in paired live status.

This experimental release supports sender-only message actions and preparation-time text-script logic. Events, block health, mutable runtime state, persistent gameplay variables and UI are recorded in the [dependency backlog](docs/ecosystem/backlog.md). Published versions begin at 0.0.1; milestone labels describe engineering phases.

## Get started

1. Stop Paper, back up existing AetherScript data, and install **AetherScript-0.0.5.jar** from the release. Keep only one plugin JAR installed.
2. Start with Java 25 and wait for `Ready. Restored ...`. A new install creates and activates `plugins/AetherScript/scripts/hello-ai.yml`.
3. Run `/ais run hello-ai`. Players need `aetherscript.example.hello` (operators by default).
4. Edit its quoted `text`, save, then `/ais validate hello-ai` and `/ais reload hello-ai`.
5. Run it again: the new message is live without restarting Paper.

Console commands omit `/`. Administration requires `aetherscript.admin`. There is no automatic file watcher or `/helloai` alias; `commands.name` is reserved metadata.

```text
/ais status [feature]
/ais list
/ais validate [feature]
/ais reload [feature]
/ais run <feature>
/ais history <feature>
/ais diff <feature>
/ais rollback <feature>
```

You edit `scripts/<id>.yml`, `.yaml`, `.json` or `.aether`; use one format per ID. Internal revisions are managed by the engine. Invalid reloads show the edit target and preserve working source. Rollback does not overwrite your edit and stays selected across restarts until explicit reload.

**Upgrade change:** valid files now autoactivate on startup. Invalid/missing files can recover saved revisions. 0.0.1 had no durable history, so the first successful activation on 0.0.2 or later creates it. Full `/ais reload` replaces the whole registry and retires features whose files were removed; targeted reload affects one feature.

[Installation](https://mistdig.github.io/AetherScript/guide/getting-started.html) · [Language](https://mistdig.github.io/AetherScript/guide/language.html) · [Recovery](https://mistdig.github.io/AetherScript/guide/recovery.html) · [Test checklist](https://mistdig.github.io/AetherScript/guide/testing.html)

## Build and verification

Set `JAVA_HOME` to JDK 25:

```powershell
.\gradlew.bat clean check build
python integration-tests/mcp_stdio.py
```

Linux/macOS: `./gradlew clean check build`. Gradle 9.1.0 wrapper, dependency locks and checksums are committed. First build needs network access.

- `plugin-paper/build/libs/plugin-paper-0.0.5.jar`: standalone plugin; do not install the thin JAR.
- `gateway-mcp/build/libs/gateway-mcp-0.0.5.jar`: separate executable MCP process.

Verification history: 0.0.2 passed **70 automated tests**, the packaged MCP check, and **28 Paper console checks across five boots and 100 distinct reloads**. The 0.0.3 build repeats those automated tests and packaged MCP verification. Current 0.0.4 results are recorded in the [test notes](docs/testing.md). Tests cover invalid edits, restart, rollback, retention, corrupt storage and abruptly halted JVM saves. See [0.0.2 server evidence](docs/evidence/0.0.2) and [testing notes](docs/testing.md). Connected-player 0.0.4 checks, Purpur, named MCP clients and performance benchmarks remain open.

## Read-only MCP

Install the matching plugin and `AetherScript-MCP-0.0.5.jar`. Run `ais pair` from the server console, then use the endpoint it prints:

```text
java -jar AetherScript-MCP-0.0.5.jar pair --endpoint http://127.0.0.1:49152 --profile C:/AetherScript/private/aether-profile.json
```

Enter the code at the prompt. Replace the sample endpoint and paths with your installation. Non-secret endpoint information is also written to `plugins/AetherScript/connection-details.json`.

After pairing, a Codex CLI entry can launch the local stdio gateway:

```text
codex mcp add aetherscript -- java -jar C:/AetherScript/AetherScript-MCP-0.0.5.jar serve --profile C:/AetherScript/private/aether-profile.json
```

Use Java 25 on the executable path. `java -jar AetherScript-MCP-0.0.5.jar` with no arguments starts offline authoring. In paired mode, `aether_server_status` reads version/readiness/generation/feature-count information. `aether_capabilities` provides the language rules/template and `aether_validate` checks supplied source. The live grant has `status` scope. AI deployment remains gated by the server-authority requirements in the security policy.

The server console's `ais unpair` revokes access and closes the listener. `forget --profile PATH` removes the local credential. For a remote host, use an authenticated SSH loopback tunnel and pair against its local forwarded endpoint. The [full pairing guide](https://mistdig.github.io/AetherScript/guide/security.html) contains copyable Codex configuration and the [official OpenAI MCP configuration reference](https://learn.chatgpt.com/docs/extend/mcp?surface=cli).

0.0.4 remains an offline-authoring release. Upgrade both artifacts for pairing. Named-client verification is tracked in the [MCP compatibility matrix](docs/mcp-clients.md).

## Reliability and performance

The engine stages content and atomically replaces a checksummed manifest. Only committed revisions participate in recovery. Corrupt manifests block startup; individual damaged sources can fall back to older valid copies. A save/audit failure attempts compensation; uncertain recovery quarantines operations. Registry rollback does not undo messages already sent.

Process-crash tests are not a power-loss guarantee; filesystem directory flush support varies. Keep owner backups of the whole data directory. Local audits are not tamper-proof and need disk monitoring/rotation. Host administrators and installed Java plugins remain trusted.

AetherScript shares Paper's JVM GC. It does not force or disable collection. Parsing and disk I/O run outside normal invocation; actions and queues are bounded. Zero allocation, GC-free execution and zero lag are not claimed. See [architecture](docs/architecture.md), [security](SECURITY.md) and [performance](docs/performance.md).

## Contributing and releases

Every completed change batch gets a version, changelog and GitHub release with plugin/MCP JARs, source archive and SHA-256 checksums. Published tags/assets are not overwritten. See [release procedure](docs/releasing.md) and [contributing](CONTRIBUTING.md).

Website source lives in `site/`; guides are generated from `docs/wiki/` so the docs and wiki share their content. Cloud artwork lives in `docs/assets/`. See [website development](site/README.md). Runtime worlds, caches and credentials are excluded. Apache-2.0.
