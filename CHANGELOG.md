# Changelog

Every published change batch receives a new version and GitHub release. Dates use Asia/Calcutta. Entries distinguish tested behavior from planned capabilities.

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
