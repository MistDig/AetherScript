# Contributing

Use JDK 25, the committed wrapper, and `./gradlew clean check build`. Add behavioral tests for parser/runtime/security changes. Run the disposable-server smoke harness for Paper adapter changes; keep real-server evidence separate from mocks. Update schema/examples when the contract changes.

Dependency changes require reviewed version pins, lockfile updates, and SHA-256 verification metadata. Do not regenerate checksums to bypass a mismatch without investigation. Use no production worlds for integration tests. Do not add mutating MCP tools ahead of the security gates in SECURITY.md.
