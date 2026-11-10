# Changelog

Every published change batch receives a new version and GitHub release. Dates use Asia/Calcutta. Entries distinguish tested behavior from planned capabilities.

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

### Verification

- Java 25 / Gradle clean check build: 70 tests passed, zero failures/errors/skips.
- Packaged MCP subprocess passed.
- Paper 26.3 build 245: 27 console checks across five boots and 100 distinct live reloads passed, including restart recovery, rollback, retention and corrupted storage.
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
