# Actual test results

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
