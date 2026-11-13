# Actual test results

## Release 0.0.7 — shared browser approval and runtime inputs

Java 25 clean check build passed 183 tests. Five relay tests cover v1 regressions and v2 browser grants, OAuth S256 PKCE, replay prevention, exact IDs, live-status confirmation, revocation and retention. The local gateway passed protocol and AI-authoring subprocess checks. Complete runnable wiki examples, including deliberately invalid syntax, were checked by the packaged compiler.

A fresh disposable Paper 26.3 build 245 installation generated its own identity and console link. Edge performed browser approval and returned authorization to a desktop HTTP test client. The installed Codex app-server discovered all six hosted tools using the resulting bearer. The hosted MCP created a native script, evaluated two different argument lists, captured the expected output, rejected an ungranted target, and returned a source location for invalid syntax. Paper console invocation exercised a quoted name. Restart preserved the connection and script; console disconnect revoked access.

This live run used the public Cloudflare service and local browser/server processes. It did not use the owner's two-PC Feather server or interactive AI chat. ChatGPT, Claude, Cursor and Cline remain untested as actual clients. The Codex check verifies hosted discovery, not a full native OAuth chat session.

The final JAR also passed 31 Paper recovery checks across five boots and 100 distinct reloads, including loopback pairing, rejected edits, restart, rollback and corrupted storage.

The human site exports every wiki page into a versioned full guide. Generated link/search/export checks and Edge copy/download controls passed. [Versioned evidence](evidence/0.0.7/)

## Release 0.0.6 — Cloudflare script management

Java 25 clean check build passed 152 tests including abrupt JVM exits during remote script publication. Three relay tests passed. The shipped gateway passed offline authoring and protocol tests. On Windows the Codex app-server discovered nine relay-mode tools. The Windows launcher paired the real gateway and passed the expected arguments to an isolated Codex registration command.

Using the deployed Cloudflare Worker and a disposable Paper 26.3 build 245 server, the MCP created a native script, read back the active revision, edited it, compared captured messages with expected output and observed the new message through Paper. It rejected stale writes, invalid scripts, an ungranted feature and access after revocation. This setup used an internet relay between processes on one test machine. The owner's two-PC Feather installation still needs its own check. [Evidence](evidence/0.0.6/)

## Release 0.0.5 — paired MCP

Java 25 clean check build passed 138 tests. The shipped gateway passed the offline MCP and AI authoring subprocess checks. On a disposable Paper 26.3 build 245 server the gateway exchanged a console code, read generation 1, observed generation 2 after a script reload, and lost live access after console revocation. The full run exercised 31 checks, five boots and 100 changed reloads. This run used a local connection. Remote hosting and named chat client setup require their own checks. [Release evidence](evidence/0.0.5/)

## Release 0.0.4 — native language and AI editing

The Java 25 clean build passed all 113 tests. The packaged authoring test performed seven checks, including a valid single-line edit, comment-only edit and precise type error through both CLI and MCP. The packaged MCP stdio protocol check passed independently.

Paper 26.3 build 245 passed 28 console checks on the final plugin across five boots and 100 distinct reloads of a native script. These exercised validation, changed messages, rejected edits, rollback, restart and corrupted storage. A connected-player test of the new version remains open. The documentation checks passed on 13 pages with five full-text search queries. [Verification records](evidence/0.0.4/)

## Release 0.0.3 — documentation site

Java 25 clean check build passed all 70 automated tests and the packaged MCP process passed with 0.0.3 metadata. Documentation production build generated 12 pages; 384 local links/assets/anchors and four real MiniSearch queries passed. npm audit reported zero findings after the pinned Vite 6.4.6 override. Evidence is under `docs/evidence/0.0.3/`.

The site reuses VitePress navigation, local search and appearance controls, with custom responsive/light/dark styles and native copy-example feedback. No computer-use/browser automation was used: these are build, generated-output and search-index checks, not interactive or pixel-level visual tests. Initial checks caught the generated default 404 lacking static navigation; a custom 404 was added, and its public asset path was corrected before the final passing build.

Engine behavior is unchanged from 0.0.2. The real Paper run below belongs to 0.0.2 and was not repeated or relabeled for this docs release.

## Release 0.0.2

Local Java 25 / Gradle 9.1 clean check build passed: **70 tests, zero failures/errors/skips** (35 existing core, 28 recovery, 7 protocol). Interrupted writes and three separate JVM halts cover pre-replacement and post-commit recovery. Additional cases cover retention reduction while recovering older content, corrupt manifests/content, rollback holds, unchanged/invalid saves, settings bounds and source diagnostics.

The packaged MCP process passed. Real Paper 26.3 build 245 passed **28 console checks over five boots, including 100 distinct live reloads**, invalid-edit restart recovery, retained rollback, explicit release of rollback hold, five-version pruning, corrupt active-content fallback and corrupt-manifest startup blocking. No live server was used. Evidence is in `docs/evidence/0.0.2/`; initial local execution was November 9; the final clean build and packaged MCP passed again November 10, 2026 (Asia/Calcutta). A final startup fix avoids creating a conflicting bundled YAML example when JSON or .yaml source already exists. The entire Paper suite was repeated November 10 with an existing JSON example: 28 checks passed. Evidence and hashes describe these final binaries.

The first new unit run had two wrong expected line numbers (12 instead of the fixture's actual 11); corrected assertions then passed. No parser-location bug was hidden. The new Paper harness passed its complete run. Console tests are not connected-player or performance tests. The owner reported 0.0.1 in-game reload, invalid-edit preservation and permission denial working; 0.0.2 player acceptance remains for the checklist. Purpur and named MCP clients remain unverified.

Below is historical evidence, preserved as the status at those earlier milestones.

## Release 0.0.1 — 2026-11-09

Rebuilt with the single release version in `gradle.properties`: `clean check build` passed, with 42 tests and no failures/errors/skips. The packaged MCP subprocess also passed and checked the plugin metadata, engine build properties, and MCP version all report 0.0.1. Separate release evidence is in `docs/evidence/0.0.1/`. The disposable Paper 26.3-245 console harness passed startup, live A-to-B reload, invalid-update preservation, and 100 repeated reloads for this version. Connected-player and Purpur tests remain unrun.

The first GitHub CI run failed closed because verification metadata omitted `com.fasterxml.jackson:jackson-base:2.19.2`'s parent POM. Its SHA-256 was checked against Maven Central's published checksum and added without disabling verification. A subsequent full build from an empty Gradle user-home downloaded every dependency and passed all 42 tests; see `docs/evidence/0.0.1/fresh-cache-build.txt`.

Source inspection found no explicit `System.gc()`/`Runtime.gc()` calls in engine/plugin/gateway Java sources. This is not a GC-free execution or performance benchmark claim; see `docs/performance.md`.

## Historical development verification

Date: 2026-11-09, Asia/Calcutta. Windows amd64; Oracle JDK 25.0.2+10; Gradle 9.1.0. Paper 26.3 build 245. These results apply to the implementation and packaging milestones recorded in local Git; no public release or deployment is claimed.

## Build and automated tests

Executed: `gradlew.bat clean check build --write-locks --write-verification-metadata sha256 --no-daemon`.

**BUILD SUCCESSFUL**. A subsequent `--offline --no-daemon clean check build` also passed in both the working repository and a fresh source extraction with strict dependency verification. After normalizing resource line endings and sorting shaded dependencies, both JARs were byte-for-byte identical across those two builds; hashes are in `evidence/reproducible-artifacts.json`. This comparison used the same OS/JDK and cached dependencies, not a second independent toolchain.

42 tests, 0 failures/errors/skips: 35 core cases and 7 MCP protocol cases. XML evidence is under `docs/evidence/`.

Covers format-equivalent IR, distinct source hashes, unknown/missing fields, unsafe YAML tags/anchors/aliases, duplicate YAML/JSON keys, multiple documents, strict JSON, unsupported operations/targets, byte/depth/text/action bounds, malformed UTF-8, filename/ID mismatch, duplicate IDs, missing sources, invalid-update preservation, 100 replacements, sender permissions, revocation/retirement during actions, stale generation, immutable snapshots, command collision, audit errors, protocol initialization/discovery, denied mutation methods/tools, stdio framing, oversized frames, and honest offline status.

The actual packaged gateway also passed `integration-tests/mcp_stdio.py`: initialization, discovery, validation, offline status, and rejected deployment, all through subprocess stdio. Evidence: `evidence/mcp-stdio-process.json`.

The Paper adapter compiles and is exercised on a real server; it has no mock unit-test suite. Parser tests do not prove complete sandboxing or every attack is impossible.

## Real Paper test

Disposable server bound to `127.0.0.1:25591`, offline mode, 1.5 GiB max heap, view/simulation distance 2. No production world touched. Server started and shut down normally.

Passed: plugin enable, readiness, validation, initial activation, message A, live reload to B, invalid replacement rejection, unchanged generation 2 and B, 100 additional reloads, generation 102, one active feature, and B still dispatching. Evidence: `evidence/paper-26.3-245-smoke.json`. The portable harness was repeated against the final reproducible plugin JAR from implementation commit `b63c6fd`; all checks passed. All invocations used the console, **not a connected player**. No quantitative memory/leak or TPS benchmark was performed.

The first harness attempt failed because it sent `validate` before asynchronous initialization completed. Plugin returned `NOT_READY`. The harness was corrected to wait for readiness; the repeat completed successfully. This was a test-harness synchronization failure, not a hidden pass.

## Acceptance status

| Handoff ID | Status | Evidence / gap |
|---|---|---|
| A01 build | Pass locally | Wrapper, pins, locks, checksums; CI defined but not executed remotely |
| A02 startup | Pass | Real Paper 26.3-245 |
| A03 formats | Pass | Shared IR and duplicate-ID tests |
| A04 permissions | Partial | Runtime unit tests; connected-player test not run |
| A05 valid reload | Partial | Console AÃ¢â€ â€™B passed; connected-player continuity not run |
| A06 rejected reload | Pass | Unit and real Paper behavior/generation preserved |
| A07 repeated reload | Partial | 100 unit and server reloads; resource profiling not run |
| A08 alias lifecycle | Not run | No alias shipped |
| A09 parser attacks | Pass for tested corpus | Bounded hostile-input tests; not formal verification |
| A10Ã¢â‚¬â€œA19 security/deployment | Not implemented | Mutating bridge absent; future v0.2 gates |
| A20 MCP clients | Partial | Automated protocol tests only; named clients untested |
| A21Ã¢â‚¬â€œA22 events/GUI | Not implemented | Future work |
| A23 load | Partial | Static action/queue limits; no load benchmark |
| A24 Purpur | Not run | No compatibility claim |
| A25Ã¢â‚¬â€œA26 rollback/approval | Not implemented | Future gates |

Full v0.1 release acceptance remains open until connected-player and resource checks are completed. No AI deployment can be enabled by configuration.

## Reproduce integration checks

Build first, then:

```text
python integration-tests/mcp_stdio.py --java /path/to/jdk25/bin/java
python integration-tests/paper_smoke.py --java /path/to/jdk25/bin/java --server-jar /path/to/paper-26.3-245.jar --work-dir /new/disposable/directory --accept-eula
```

Review Minecraft's EULA before passing `--accept-eula`. The harness refuses an existing work directory and verifies the server checksum, uses loopback port 25591, records evidence there, and stops its server in `finally`. Port 25591 must be free. The console test is not a simulated connected-player test.
