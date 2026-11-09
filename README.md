# AetherScript

[![Build](https://github.com/MistDig/AetherScript/actions/workflows/ci.yml/badge.svg)](https://github.com/MistDig/AetherScript/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/MistDig/AetherScript)](https://github.com/MistDig/AetherScript/releases)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

An open-source Minecraft scripting engine with live hot reloading and a path toward secure, vendor-neutral AI tooling.

**[Download 0.0.1](https://github.com/MistDig/AetherScript/releases/tag/v0.0.1)** · [Changelog](CHANGELOG.md) · [Roadmap](docs/roadmap.md)

## First release

Version **0.0.1** is the first experimental release. It implements the initial local-command slice of the roadmap; the roadmap's v0.1 acceptance milestone is still open. Install `AetherScript-0.0.1.jar` on Paper; run `AetherScript-MCP-0.0.1.jar` separately only if you want the offline MCP prototype. Release assets include SHA-256 checksums.

Verified locally on Paper 26.3 build 245: startup, message changes without restart, invalid-update preservation, and 100 consecutive reloads. The automated engine/protocol suite has 42 tests. Connected-player continuity, Purpur, and named MCP client interoperability remain untested.

A Java 25 scripting plugin targeting **Paper 26.3 build 245**. Current version: **0.0.1**. Apache-2.0.

Implemented: strict YAML/JSON → immutable typed message actions, permission-checked `/ais run`, explicit live script reload, SHA-256 revisions, monotonically increasing registry generations, bounded background I/O, basic local JSONL audit, and a separate read-only MCP stdio prototype.

**AI deployment is absent.** No network bridge, arbitrary console commands, file-writing MCP tools, script evaluation, or embedded LLM. The gateway is an offline validator; it cannot inspect a running server. This is not a production-ready autonomous deployment system.

## Build

Install a JDK 25 and set `JAVA_HOME` to it. From this directory:

```powershell
.\gradlew.bat clean check build
```

On Linux/macOS: `./gradlew clean check build`. Gradle 9.1.0, dependency locks, wrapper SHA-256, and dependency verification metadata are committed. First build requires network access. No local server installation is needed for unit tests.

Artifacts:

- `plugin-paper/build/libs/plugin-paper-0.0.1.jar` — standalone plugin with relocated parser dependencies. Do not install the `-thin` JAR.
- `gateway-mcp/build/libs/gateway-mcp-0.0.1.jar` — separate executable MCP process.

See [actual test results](docs/testing.md) and [platform pins](docs/compatibility.md). A build is not proof of player/client compatibility.

## Use locally

1. Install the plugin JAR into a disposable Paper 26.3 server's `plugins/` directory; start with Java 25.
2. Wait for `AetherScript: Ready` in the log. The plugin writes `plugins/AetherScript/scripts/hello-ai.yml` if missing. **Startup begins with no active features.**
3. As console or an administrator with `aetherscript.admin`, run `ais validate`, then `ais reload`.
4. Run `ais run hello-ai`. Players additionally need the script's `aetherscript.example.hello` permission (default: operators only).
5. Edit the sample text and run `ais reload hello-ai`; the next invocation uses the new message without restarting. Invalid replacements preserve the active snapshot and generation.

In game, prefix commands with `/`. Administrative operations: `status`, `list`, `validate [feature]`, `reload [feature]`. `run <feature>` checks the feature permission on every invocation and before each action. No `/helloai` alias is registered; the script's command name is reserved metadata only.

`reload` without an ID validates every source and replaces the entire registry, so removed files retire features. `reload <id>` updates only that feature. A filename must equal its logical ID plus `.yml`, `.yaml`, or `.json`. Use one encoding per feature, not both provided examples. There is no automatic filesystem watcher.

Limits: 64 KiB source, nesting depth 32, 1 command per feature, 1–32 message actions, 2,048 UTF-16 units per message, 128 active features, 256 directory entries, one validation/reload at a time. Plain text is sent only to the invoker. No loops, expressions, variables, rich formatting, events, inventories, or arbitrary plugin reloading.

## Read-only MCP prototype

```text
java -jar gateway-mcp/build/libs/gateway-mcp-0.0.1.jar
```

Configure a stdio MCP client with that executable/argument array and an absolute JAR path. The process reads newline-delimited JSON-RPC on stdin and emits only protocol JSON on stdout. It supports protocol `2025-06-18`, initialization, ping, tool discovery, `aether_capabilities`, `aether_server_status` (explicitly offline), and `aether_validate` (caller-supplied source). No model API key is needed.

“Universal” describes the vendor-neutral protocol design. Codex, Claude, Cursor, and Cline have **not yet been interoperability-tested**; see [matrix](docs/mcp-clients.md). Prototype tooling is intentionally ahead of the v0.3 gateway milestone, without its server or deployment claims.

## Performance

AetherScript shares Paper's JVM garbage collector. It does not force garbage collection or change server GC settings. The engine compiles scripts during validation/reload, keeps file I/O off the game thread, and bounds runtime actions and queues. This reduces avoidable work; it is not a zero-allocation or zero-lag guarantee. See the [performance policy and profiling plan](docs/performance.md).

## Reliability boundaries

The v0.1 registry is in memory. Restart requires explicit local activation; edited files are never silently autoactivated. There is no durable revision store, automatic rollback, transaction recovery, or guarantee that an acknowledged revision survives a process crash. These are v0.2 work.

Reload records a flushed audit intent before activation and a result afterward. Audit failure before activation rejects the change. Result-write failure restores prior features as a **new generation**; messages already sent cannot be undone. A process crash between these steps is not recovered automatically. Local logs are not tamper-proof and currently have no rotation; owners must monitor disk space. Filesystem checks reduce traversal/symlink risk but do not isolate a malicious host administrator or concurrent privileged filesystem editor.

See [security](SECURITY.md), [architecture](docs/architecture.md), [script schema](schema/feature-v1.schema.json), and the [expanded roadmap](docs/roadmap.md).

## Releases and development

The canonical repository is [MistDig/AetherScript](https://github.com/MistDig/AetherScript). Every completed change batch receives a new version and GitHub release with a changelog, including documentation changes. The first release is **0.0.1**; small follow-up changes start at **0.0.2**. Published tags/assets are not overwritten.

See [release procedure](docs/releasing.md), [contributing](CONTRIBUTING.md), and [Apache-2.0 license](LICENSE). Source, examples, schemas, tests, evidence, and documentation are versioned in Git; built binaries are attached to GitHub releases. Runtime worlds, credentials, caches, and disposable server data are excluded.
