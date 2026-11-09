# Release procedure

Owner policy: every completed change batch, including documentation changes, gets a new version, changelog entry, and GitHub release. Versioning starts at **0.0.1**; use the next patch version for small changes. Roadmap milestones are scope targets, not automatic release numbers. Do not overwrite published releases/tags/assets.

1. Update `version` in `gradle.properties` (the single source for plugin metadata and engine/MCP status). Update README artifact/download references; the integration harnesses read the version automatically.
2. Add a dated `## [X.Y.Z] - YYYY-MM-DD` section to `CHANGELOG.md`, listing actual changes, tests, and limitations.
3. Run `./gradlew clean check build` and `python integration-tests/mcp_stdio.py`. Run the disposable Paper harness when engine/platform/packaging changes warrant it. Save truthful evidence without rewriting historical test results.
4. Run `python scripts/prepare_release.py --tag vX.Y.Z` to check version/changelog consistency and prepare JARs, source ZIP, notes, and SHA-256 checksums. The source archive reflects HEAD, so final packaging must follow the release commit.
5. Commit the completed batch, push `main`, and wait for build CI to pass. Create an annotated `vX.Y.Z` tag at that commit, then push the tag.
6. The release workflow independently builds/tests, runs the MCP process check, packages artifacts, and creates the GitHub release from the exact changelog section. It fails if the release already exists; it never replaces published assets.
7. Wait for the workflow to finish and verify the release link, tag SHA, and asset list before reporting publication complete. If a build fails before publication, repair it before publishing; never report a failed workflow as a release.

Git holds implementation, docs, examples, schemas, test harnesses, and compact evidence. GitHub Releases hold binaries/source archives/checksums. Do not commit server worlds, caches, credentials, local environment files, or downloaded server JARs.
