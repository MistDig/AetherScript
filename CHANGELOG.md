# Changelog

Every published change batch receives a new version and GitHub release. Dates use Asia/Calcutta. Entries distinguish tested behavior from planned capabilities.

## [0.0.12] - 2026-11-15

- Add homogeneous immutable lists, typed empty constructors, indexing, length/append, typed function list parameters/results and bounded for iteration. Structural equality shares the execution budget; list size/depth are capped.
- Add trailing typed function defaults with immutable constant scalar/list values, canonical semantic hashes and preserved no-default compatibility.
- Add a separate core SQLite variable backend using prepared statements, revision CAS batches, strict schema/codecs and explicit worker-thread ownership. Script bindings and automatic migration remain open.
- Pin and verify the SQLite JDBC provider; retain licenses and verify both packaged JARs against real native-backed storage.
- Verified 254 Java tests, both packaged SQLite providers/licenses, nine wiki examples, packaged CLI/MCP authoring and seven published 0.0.11 semantic-hash cases. Paper passed 19 runtime/command checks across two boots. Updated language/AI guides and the still-open halfway checkpoint.

## [0.0.11] - 2026-11-15

- Add mutable invocation locals, bounded while/repeat loops, nearest-loop break/continue, typed scalar functions and return values. Calls have isolated frames, source-linked errors, shared execution/output budgets and bounded self-recursion. Legacy static IR hashes remain compatible.
- Register each active feature's real Paper command name with permissions/sender rules, collision protection, owned cleanup and failed-activation restoration.
- Add a core-only typed persistent-variable service with revision CAS, atomic batches, checksummed JSON snapshots and interrupted-save tests. SQLite, script bindings and the browser variable editor remain open.
- Preserve the complete ecosystem brief and track M1–M3 halfway gates without claiming half the concrete capabilities are complete. Update language/AI contracts and runnable examples.
- Verified 234 Java tests, seven relay tests, packaged CLI/MCP authoring and seven wiki examples. The final Paper JAR passed 18 runtime/command checks across two boots and 28 recovery checks across five boots with 100 changed reloads. Connected-player command-tree/suggestions and broader gameplay remain unverified.

## [0.0.10] - 2026-11-14

- Put a current AI reference before the complete human-wiki appendix. Corrected hosted/local tool availability, full-access permissions, runtime syntax, limits, validation errors and uncertain-apply recovery.
- Added a separately readable ai-reference.txt and verified runnable generation examples with the packaged compiler.
- Audited README, commands, setup, roadmap, architecture and dated ecosystem reports against the implementation and versioned evidence. Corrected remote-operation boundaries and both execution modes; added a focused consistency check.

## [0.0.9] - 2026-11-14

- Applied the supplied website motion brief to the documentation website with an original cloud-themed visual design and functional script workbench.
- Added meaningful motion and responsive interaction states with keyboard and reduced-motion support. The site preserves documentation routes, search, copy/download controls and versioned AI-readable guides.

## [0.0.8] - 2026-11-14

- Added an explicit full-access approval for creating and editing native scripts, alongside selected-script access. Existing restricted connections require fresh approval to gain broader access.
- Fixed repeated Paper rejection messages by tracking grant state, expiring pending enrollment and clearing superseded links.
- Improved hosted OAuth discovery, dynamic registration for web callbacks, and browser setup for clients requiring manual credentials.
- Superseded, rejected, expired and revoked approvals return terminal authentication errors. Acknowledged active connections survive starting another connection.

- Raised the daily per-IP enrollment/registration budget to 40 to accommodate multi-client setup and retries, with explicit owner approval. Global and per-server quotas also apply.

- Verified 190 Java tests and seven relay tests, plus packaged MCP checks and runnable wiki examples. Live Edge/Paper tests cover web-secret OAuth, broad and selected permissions, existing edits, new IDs, restart and revocation. Actual Gemini product testing remains pending.

## [0.0.7] - 2026-11-13

- Added console `ais connect`, browser approval and hosted OAuth MCP. Fresh servers generate their identity automatically and connect through the shared AetherScript service.
- Added exact per-app feature grants, persistent restart reconnection, `ais connections` and immediate/durable `ais disconnect` revocation.
- Added a browser page with AI app setup choices and the registered callback destination. Connected is confirmed after an authorized app reads live Paper status.
- The Windows Codex setup now registers hosted MCP and starts native OAuth login. The local MCP JAR remains available for existing workflows.
- Added bounds and retention for public enrollment, app registration, grants and pending work. Management connections expire after 30 days.

- Added native typed command arguments and invocation-time conditions. Test tools accept argument values; existing scripts retain their original behavior.
- Added troubleshooting and MCP reference guides, complete versioned AI documentation, and Copy docs link and Download docs controls. Runnable examples are checked by the shipped compiler.

- Verification passed 183 Java tests, five relay tests, packaged authoring/protocol checks, and all runnable wiki examples. Real Edge consent and installed Codex hosted discovery were checked with a disposable Paper server. The recovery regression passed 31 Paper checks across five boots and 100 changed reloads. See the versioned test records for exact boundaries.

## [0.0.6] - 2026-11-12

- Added a personal Cloudflare Worker that pairs Paper and a local MCP through outbound HTTPS. Server owners enroll using a private configuration and grant named native script features from the server console.
- Added MCP tools to list, read, validate, create, update and test a granted script. Edits include the expected source hash. Script tests report captured messages from a synthetic sender and compare them with the requested output.
- Added a Windows setup ZIP containing the MCP JAR and a launcher that pairs it and configures Codex. The separate Paper plugin JAR belongs on the Minecraft host.
- Added a local script test tool to the offline MCP. The 0.0.5 loopback status workflow remains available.
- The personal relay can see script source and output. Its limits include one in-flight request per session, a 64 KiB JSON envelope and a 24-hour management session. Console revocation and server restart end the server-side grant.

- Verified 152 Java tests, three relay tests, packaged MCP subprocesses and Codex tool discovery. The live relay test exercised authorized script creation, editing, actual output, stale-write rejection and revocation on Paper 26.3 build 245.

## [0.0.5] - 2026-11-12

- Added console pairing and revocation for a read-only Paper status connection. The server provides a loopback endpoint and a short-lived, single-use code. The connection-details file contains no credential.
- Added local pairing profiles to the MCP JAR. A paired client can read readiness, feature count and generation. It can still validate scripts offline. Script activation stays with the server administrator.
- Published separate setup steps for clients running on the Paper host and clients connecting over an SSH tunnel.

- Verification passed 138 automated tests, offline subprocess checks and 31 real Paper checks across five server boots and 100 changed reloads, including live MCP status and revocation.

## [0.0.4] - 2026-11-11

- Added native `.aether` command scripts with immutable local bindings, checked integer arithmetic, booleans, text conversion and conditionals. The compiler prepares sender messages during validation and reload. Paper checks the sender and permission on invocation.
- Integrated native scripts with the existing revision store, rollback, diagnostics and restart recovery. YAML and JSON remain supported.
- Published a machine-readable language contract, a complete editable template and an offline CLI. The read-only MCP exposes the same contract and compiler. The packaged authoring test checks a one-line change, an invalid expression and CLI/MCP agreement.
- Added the language design decision, ecosystem gap report, 154-row capability matrix and dependency backlog. The matrix records existing evidence and future adapter work.
- Added a copyable native example to the documentation homepage and a guide to authoring and validating edits. See the versioned verification records under `docs/evidence/0.0.4/`.

- Verified 113 automated tests, seven packaged AI authoring checks and 28 Paper console checks across five boots and 100 distinct native-script reloads on Paper 26.3 build 245.

## [0.0.3] - 2026-11-10

### Added

- Dedicated GitHub Pages documentation at https://mistdig.github.io/AetherScript/ using VitePress.
- AetherScript homepage inspired by TicketPilot's blue gradients and rounded panels, with the existing smiling cloud mascot and original copy/artwork.
- MistTags-style documentation structure: local full-text search, sidebar guides, dark/light appearance, mobile navigation, page outlines and copyable examples.
- Nine guides generated from the existing wiki sources, plus changelog and a custom not-found page.
- Pinned npm lockfile, patched Vite 6.4.6 build override (npm audit: zero findings), automated generated-link/anchor/search checks, and a Pages build/deploy workflow.
- Wiki entry points and README links to the new documentation site.

### Verification and scope

- Documentation production build passed: 12 generated pages, 384 local links/assets/anchors checked, and four real full-text search queries passed.
- Java 25 clean check build passed all 70 tests; the packaged MCP process passed for 0.0.3.
- Engine behavior is unchanged from 0.0.2. Real Paper evidence remains the 0.0.2 run; no new gameplay or server compatibility claim.
- No computer-use/browser automation was used. Responsive and theme styling is implemented, but an interactive browser visual test is not claimed.

## [0.0.2] - 2026-11-10

### Added

- Durable successful source revisions with checksummed metadata, startup recovery and configurable retention (default five, range 1–50).
- `/ais history`, `/ais diff`, `/ais rollback` and per-feature status distinguishing running source from the editable file.
- File/line/column/field diagnostics, with invalid-target correction; structured MCP locations use the same parser.
- Rollback held across restarts without overwriting editable source; explicit reload releases the hold.
- Recovery, retention, corruption, interrupted-save and abruptly halted child-JVM tests.
- Cloud-themed README and 13 wiki pages covering installation, language, commands, recovery, diagnostics, testing, security, performance and expanded gameplay roadmap.

### Changed

- Valid editable sources activate automatically at startup. Invalid/missing files can recover saved working source; corrupt metadata blocks startup.
- Invalid edits and unchanged reloads consume no history. Unchanged reloads do not advance registry generation.
- Storage/audit failure attempts compensation; uncertain restoration quarantines operations.

- Startup preserves an existing JSON/.yaml hello example instead of creating a duplicate bundled YAML file.

### Verification

- Java 25 / Gradle clean check build: 70 tests passed, zero failures/errors/skips.
- Packaged MCP subprocess passed.
- Paper 26.3 build 245: 28 console checks across five boots and 100 distinct live reloads passed, including restart recovery, rollback, retention and corrupted storage.
- See `docs/evidence/0.0.2/` for local evidence.

### Upgrade and limitations

- Stop Paper, back up the data directory, replace the old JAR, then restart. 0.0.1 has no revision history; first successful 0.0.2 activation creates it.
- Only message actions exist. Block health/events and richer gameplay remain planned. AI deployment and server connectivity remain absent.
- Atomic process-crash recovery is not a power-loss guarantee; filesystem directory-flush support varies. Keep owner backups.
- 0.0.2 connected-player checks, Purpur, named MCP client interoperability and resource benchmarks remain open.

## [0.0.1] - 2026-11-09

First experimental release. The previous local `0.1.0-dev` name was an unpublished development label; published versioning starts at **0.0.1**.

### Added

- Paper 26.3 build 245 plugin on Java 25, with `/ais status`, `list`, `validate`, `reload`, and `run`.
- Strict bounded YAML/JSON parsing, immutable typed message actions, source/IR hashes, and explicit sender permissions.
- Live registry swaps, generation tracking, rejected-update preservation, and basic local audit records.
- Separate read-only MCP stdio prototype for offline status, capabilities, and source validation. No deployment tools or network bridge.
- Hello feature examples, JSON Schema, documentation, security boundaries, and expanded gameplay/remote-connectivity roadmap.
- Gradle wrapper/checksums, dependency locks/verification, automated tests, disposable-server/MCP process harnesses, CI, and versioned release workflow.
- Repository README, changelog, release policy, and a single build-version source in `gradle.properties`.
- Performance policy: no forced collection, bounded work, and lower allocation pressure while retaining Paper's JVM garbage collector.
- Complete dependency verification for fresh-cache builds, including an independently verified Jackson parent POM checksum.

### Verification

- Local `clean check build`: 42 automated tests passed.
- Paper 26.3-245: startup, successful A-to-B reload, rejected-reload preservation, and 100 repeated reloads passed using console commands.
- Packaged MCP process: initialization, tool discovery, validation, offline status, and rejected deployment passed.
- See `docs/testing.md` for historical development evidence and separate 0.0.1 verification.

### Known limitations

- Startup begins with an empty in-memory registry and requires local `/ais reload`; durable recovery is future work.
- Connected-player tests, Purpur compatibility, and named MCP client interoperability are not verified.
- Events, GUIs, dynamic aliases, persistent state, and AI deployment are not implemented.
- AI deployment remains unavailable until authoritative server permissions and security gates are implemented and tested.

[0.0.1]: https://github.com/MistDig/AetherScript/releases/tag/v0.0.1

[0.0.2]: https://github.com/MistDig/AetherScript/releases/tag/v0.0.2

[0.0.3]: https://github.com/MistDig/AetherScript/releases/tag/v0.0.3
