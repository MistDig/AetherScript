# AetherScript

A Java 25 scripting plugin targeting **Paper 26.3 build 245**. Current version: **0.1.0-dev**. Apache-2.0.

Implemented: strict YAML/JSON → immutable typed message actions, permission-checked `/ais run`, explicit live script reload, SHA-256 revisions, monotonically increasing registry generations, bounded background I/O, basic local JSONL audit, and a separate read-only MCP stdio prototype.

**AI deployment is absent.** No network bridge, arbitrary console commands, file-writing MCP tools, script evaluation, or embedded LLM. The gateway is an offline validator; it cannot inspect a running server. This is not a production-ready autonomous deployment system.

## Build

Install a JDK 25 and set `JAVA_HOME` to it. From this directory:

```powershell
.\gradlew.bat clean check build
```

On Linux/macOS: `./gradlew clean check build`. Gradle 9.1.0, dependency locks, wrapper SHA-256, and dependency verification metadata are committed. First build requires network access. No local server installation is needed for unit tests.

Artifacts:

- `plugin-paper/build/libs/plugin-paper-0.1.0-dev.jar` — standalone plugin with relocated parser dependencies. Do not install the `-thin` JAR.
- `gateway-mcp/build/libs/gateway-mcp-0.1.0-dev.jar` — separate executable MCP process.

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
java -jar gateway-mcp/build/libs/gateway-mcp-0.1.0-dev.jar
```

Configure a stdio MCP client with that executable/argument array and an absolute JAR path. The process reads newline-delimited JSON-RPC on stdin and emits only protocol JSON on stdout. It supports protocol `2025-06-18`, initialization, ping, tool discovery, `aether_capabilities`, `aether_server_status` (explicitly offline), and `aether_validate` (caller-supplied source). No model API key is needed.

“Universal” describes the vendor-neutral protocol design. Codex, Claude, Cursor, and Cline have **not yet been interoperability-tested**; see [matrix](docs/mcp-clients.md). Prototype tooling is intentionally ahead of the v0.3 gateway milestone, without its server or deployment claims.

## Reliability boundaries

The v0.1 registry is in memory. Restart requires explicit local activation; edited files are never silently autoactivated. There is no durable revision store, automatic rollback, transaction recovery, or guarantee that an acknowledged revision survives a process crash. These are v0.2 work.

Reload records a flushed audit intent before activation and a result afterward. Audit failure before activation rejects the change. Result-write failure restores prior features as a **new generation**; messages already sent cannot be undone. A process crash between these steps is not recovered automatically. Local logs are not tamper-proof and currently have no rotation; owners must monitor disk space. Filesystem checks reduce traversal/symlink risk but do not isolate a malicious host administrator or concurrent privileged filesystem editor.

See [security](SECURITY.md), [architecture](docs/architecture.md), [script schema](schema/feature-v1.schema.json), and the [expanded roadmap](docs/roadmap.md).
