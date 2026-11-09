# AetherScript development
Use Java 25 and the pinned Gradle wrapper. Run clean check build before committing implementation milestones. Keep AI mutation tools absent until plugin authorization, authenticated transport, revocation, and bypass tests exist. Never substitute an older Minecraft target silently. Report real-server results separately from unit tests.

## Owner release policy

Canonical repository: https://github.com/MistDig/AetherScript . First release: 0.0.1.
For every completed change batch (including docs), increment the version in gradle.properties, update CHANGELOG.md with concrete changes and actual verification, commit/push, and publish a new GitHub release with plugin/MCP JARs and checksums. Small follow-up changes increment the patch version. Do not create a release for each intermediate edit or failed build. Never overwrite an existing tag or release asset. Follow docs/releasing.md and wait for release CI to finish. Keep historical evidence accurate; do not relabel old test runs as new results.
