# Actual test results

Date: 2026-11-09, Asia/Calcutta. Windows amd64; Oracle JDK 25.0.2+10; Gradle 9.1.0. Paper 26.3 build 245. These results apply to the implementation milestone recorded in local Git; no public release or deployment is claimed.

## Build and automated tests

Executed: `gradlew.bat clean check build --write-locks --write-verification-metadata sha256 --no-daemon`.

**BUILD SUCCESSFUL**. 42 tests, 0 failures/errors/skips: 35 core cases and 7 MCP protocol cases. XML evidence is under `docs/evidence/`.

Covers format-equivalent IR, distinct source hashes, unknown/missing fields, unsafe YAML tags/anchors/aliases, duplicate YAML/JSON keys, multiple documents, strict JSON, unsupported operations/targets, byte/depth/text/action bounds, malformed UTF-8, filename/ID mismatch, duplicate IDs, missing sources, invalid-update preservation, 100 replacements, sender permissions, revocation/retirement during actions, stale generation, immutable snapshots, command collision, audit errors, protocol initialization/discovery, denied mutation methods/tools, stdio framing, oversized frames, and honest offline status.

The Paper adapter compiles and is exercised on a real server; it has no mock unit-test suite. Parser tests do not prove complete sandboxing or every attack is impossible.

## Real Paper test

Disposable server bound to `127.0.0.1:25591`, offline mode, 1.5 GiB max heap, view/simulation distance 2. No production world touched. Server started and shut down normally.

Passed: plugin enable, readiness, validation, initial activation, message A, live reload to B, invalid replacement rejection, unchanged generation 2 and B, 100 additional reloads, generation 102, one active feature, and B still dispatching. Evidence: `evidence/paper-26.3-245-smoke.json`. All invocations used the console, **not a connected player**. No quantitative memory/leak or TPS benchmark was performed.

The first harness attempt failed because it sent `validate` before asynchronous initialization completed. Plugin returned `NOT_READY`. The harness was corrected to wait for readiness; the repeat completed successfully. This was a test-harness synchronization failure, not a hidden pass.

## Acceptance status

| Handoff ID | Status | Evidence / gap |
|---|---|---|
| A01 build | Pass locally | Wrapper, pins, locks, checksums; CI defined but not executed remotely |
| A02 startup | Pass | Real Paper 26.3-245 |
| A03 formats | Pass | Shared IR and duplicate-ID tests |
| A04 permissions | Partial | Runtime unit tests; connected-player test not run |
| A05 valid reload | Partial | Console A→B passed; connected-player continuity not run |
| A06 rejected reload | Pass | Unit and real Paper behavior/generation preserved |
| A07 repeated reload | Partial | 100 unit and server reloads; resource profiling not run |
| A08 alias lifecycle | Not run | No alias shipped |
| A09 parser attacks | Pass for tested corpus | Bounded hostile-input tests; not formal verification |
| A10–A19 security/deployment | Not implemented | Mutating bridge absent; future v0.2 gates |
| A20 MCP clients | Partial | Automated protocol tests only; named clients untested |
| A21–A22 events/GUI | Not implemented | Future work |
| A23 load | Partial | Static action/queue limits; no load benchmark |
| A24 Purpur | Not run | No compatibility claim |
| A25–A26 rollback/approval | Not implemented | Future gates |

Full v0.1 release acceptance remains open until connected-player and resource checks are completed. No AI deployment can be enabled by configuration.

## Reproduce integration checks

Build first, then:

```text
python integration-tests/mcp_stdio.py --java /path/to/jdk25/bin/java
python integration-tests/paper_smoke.py --java /path/to/jdk25/bin/java --server-jar /path/to/paper-26.3-245.jar --work-dir /new/disposable/directory --accept-eula
```

Review Minecraft's EULA before passing `--accept-eula`. The harness refuses an existing work directory and verifies the server checksum, uses loopback port 25591, records evidence there, and stops its server in `finally`. Port 25591 must be free. The console test is not a simulated connected-player test.
