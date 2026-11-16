# Contributing

Use JDK 25, the committed wrapper, and `./gradlew clean check build`. Add behavioral tests for parser/runtime/security changes. Run the disposable-server smoke harness for Paper adapter changes; keep real-server evidence separate from mocks. Update schema/examples when the contract changes.

Dependency changes require reviewed version pins, lockfile updates, and SHA-256 verification metadata. Do not regenerate checksums to bypass a mismatch without investigation. Use no production worlds for integration tests. Do not add mutating MCP tools ahead of the security gates in SECURITY.md.

## Original work and upstream references

Implement referenced capabilities independently. Keep AetherScript code, syntax, docs, examples and assets original. Do not copy, translate or relicense third-party implementation code or substantial documentation/assets without checking the exact license and permissions. References to Skript/addons describe capability goals, not bundled code, source compatibility or endorsement. Review each dependency separately and retain required copyrights, licenses and notices. Keep unverified verbatim research extracts outside public distributions.
