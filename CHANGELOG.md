# Changelog

Every published change batch receives a new version and GitHub release. Dates use Asia/Calcutta. Entries distinguish tested behavior from planned capabilities.

## [0.0.32] - 2026-11-25

- Added locally approved block-drop replacements with feature-owned item identity and optional resource-pack model keys.
- Added main-hand block interactions that can queue the same feature's command, including commands that open inventory menus.
- Added a gathering station example and disposable Paper checks for its drop and menu paths.
- Added atomic multi-ingredient and multi-output inventory exchanges, with a processing station example.
- Verification: 400 Java tests passed and one was skipped. Paper 26.3 build 245 passed 38 event and gameplay checks using translated protocol clients. Packaged MCP, legacy language compatibility, and documentation checks passed.

## [0.0.31] - 2026-11-24

- Add the invite-only Aether AI chat pilot with animated mascot states, reduced-motion support and a separate link for existing AI clients.
- Use the existing hosted relay and the server's version-matched guide to validate, activate and test one owner-approved feature. Report revision, expected and observed results, and timing. Label simulated events as previews.
- Pass seven Node tests, desktop and mobile Edge checks, and the Java 25 clean build. A deterministic model fixture passes five hosted checks on disposable Paper 26.3 build 245 running AetherScript 0.0.30, including native command execution, event previews, invalid and stale edits, and revocation.
- The app ships as source with VPS setup instructions. HeroicGamez inference, deployment, the two-PC pilot and the chest-and-gate demo require further verification or implementation.

## [0.0.30] - 2026-11-23

- Add version-matched hosted MCP documentation with concise task routes, focused guide reads, and quickstart and reference resources. The offline gateway retains its full authoring contract and serves its bundled version.
- Add owner-listed JSON/YAML scalar keys and reviewed inbox JAR proposals. File access requires a separate browser scope. Each write needs an exact console approval. Targets and rollback receipts survive restart.
- Add namespaced Minecraft item models to custom items, and document how to use ResourcePack AI Studio, RP Engine, MC GUI, Dialog Engine and 3D Engine.
- Java 25 passed 397 tests with one Windows link test skipped. All 16 relay tests passed. Packaged MCP stdio, authoring, 23 wiki examples, legacy semantic hashes and SQLite packaging passed.
- Paper 26.3 build 245 passed six hosted file and documentation checks. RP Engine 0.2.91 built the model and texture, and the AetherScript item carried the exact model component. MC GUI 0.8.6 was installed and its approved setting was edited and restored. Windows kept the loaded JAR protected and reported that rollback required stopping Paper.
- A separate Paper run verified 3D Engine 0.1.6 loaded an original OBJ and built assets while AetherScript ran on the same server. The vendor's Dialog Engine download returned HTTP 403 in this environment.
- Codex CLI 0.171.0-alpha.13 on Windows completed a blind build using a fresh feature-scoped bearer connection and two explicitly approved test tools. Paper delivered both argument-dependent messages and the real join message. This verifies the Codex CLI authoring workflow with a scripted browser OAuth fixture. Interactive setup on two PCs, client pack rendering, Dialog Engine and other AI apps remain unverified.

## [0.0.29] - 2026-11-23

- Hosted read, validate and apply can author a packed multi-file native feature with one revision and source hash.
- Add pure snapshot previews for active event handlers. Fresh browser grants can explicitly offer join, quit, block-break, block-place and cancellation capabilities. Old grants stay unchanged.
- Java 25 passed 391 tests with one Windows link test skipped. All 13 relay tests passed. Packaged authoring, MCP stdio, 22 wiki examples, legacy hashes and SQLite packaging passed.
- A scripted browser and MCP client passed nine checks on Paper 26.3 build 245 through the hosted relay, including a four-file quest repair, a scoped event preview, real player effects, restart and disconnect. AI application discovery and an unseen prompt remain open.

## [0.0.28] - 2026-11-22

- Add optional invoking-player Vault economy, Bukkit permission and WorldGuard7 build-query adapters with explicit capabilities and provider health.
- Add numeric minimum-version availability checks, exact 100-minor-unit currency conversion and bounded deposit/withdraw operations.
- Add a guarded quote/purchase example with explicit provider, permission and build-access checks and compensating refund on failed inventory delivery. Money and item changes remain separate operations with documented crash gaps.
- Java 25 passed 387 tests with one Windows link test skipped. Packaged authoring, 22 wiki examples, MCP stdio, legacy hashes, SQLite packaging and documentation checks passed.
- Paper 26.3 build 245 passed eight checks using Vault 1.7.3, EssentialsX 2.22.0, WorldEdit 7.4.6 and WorldGuard 7.0.21. The purchase debited 250 minor units and delivered four bread. WorldGuard denied a second purchase without changing inventory or balance. No actual AI client was exercised.

## [0.0.27] - 2026-11-22

- Add a public Bukkit service for owner-installed Java plugins to register versioned typed callables, discover descriptors and withdraw registration.
- Require explicit per-function script capabilities and preserve live command context and return-type checks.
- Add a standalone PersonalWeatherAddon with real Paper player-weather calls, compile-only build recipe and bounded restoration, plus a runnable script.
- Java 25 passed 383 tests with one Windows link test skipped. The addon compiled independently against the packaged SDK. Packaged authoring, wiki examples, MCP stdio, legacy hashes and documentation checks passed.
- Paper 26.3 build 245 passed 11 checks across two boots. An approved script produced a personal-weather packet through the separate addon. After addon removal, the diagnostic identified the unavailable function and unrelated scripts still ran.

## [0.0.26] - 2026-11-22



- Add bounded revision-owned entities, transient player teams and inclusive coordinate-region checks.

- Add typed damage, targeting and death snapshots with permission checks and permitted cancellation.

- Add a two-arena example with independent world-scoped scores, owned boss references and explicit cleanup. Scores are operator-driven; death-triggered scoring and automatic combat isolation remain open.

- Java 25 passed 377 tests with one Windows link test skipped. Packaged authoring, 22 wiki examples, MCP stdio, legacy hashes, SQLite packaging and documentation consistency passed.
- Paper 26.3 build 245 passed 10 checks with two translated clients. Verified simultaneous arena bosses, separate scores and teams, boss targeting, region damage handling, selective cleanup and revocation.

## [0.0.25] - 2026-11-22

- Add bounded player inventory menus with revision-owned sessions, cancelled inventory movement and selection continuations.
- Add stable feature-owned custom-item IDs and display names, including typed item persistence and matching.
- Add a runnable menu shop, machine-readable signatures and human/AI authoring guidance.
- Java 25 passed 372 tests with one Windows link test skipped. Packaged discovery and validation, 22 wiki examples, legacy hashes, SQLite packaging and documentation checks passed.
- Paper 26.3 build 245 passed 10 checks through a translated loopback client. Shift, double-click and drag input left item counts unchanged. A normal click exchanged one emerald for one tagged token. Closing or restricting the menu delivered no extra items.

## [0.0.24] - 2026-11-21

- Add feature-owned typed shared server and world state, deletion, read-set revision checks and atomic writes.
- Add a virtual economy example and stable invoking-player storage keys.
- Java 25 clean build reported 367 passing tests and one Windows symlink test skipped. Packaged AI discovery and validation, all 20 wiki examples, MCP stdio, SQLite packaging, legacy semantic hashes and documentation checks passed.
- Paper 26.3 build 245 passed 11 checks over two boots through translated loopback clients. Concurrent buyers observed one successful purchase and one conflict. The losing balance was unchanged. World isolation, deletion and restart persistence passed.

## [0.0.23] - 2026-11-21

- Add immutable nominal records with typed construction, field access, collection/function use and per-player persistence.
- Add local multi-file packages with explicit imports, bounded dependency composition, whole-package revisions and originating-file diagnostics.
- Add a four-file quest example, canonical human/AI authoring guidance and release-stamped documentation exports. Hosted multi-file mutation and external package resolution remain planned.
- Java 25 clean build reported 358 passing tests and one Windows symlink test skipped. Packaged AI discovery, original module diagnostics, wiki examples, MCP stdio, legacy hashes, SQLite packaging and site checks passed.
- Paper 26.3 build 245 passed 11 checks through a translated loopback client over two boots. Quest state persisted, an imported module reloaded independently and a broken edit retained the previous working package across restart.

## [0.0.22] - 2026-11-21

- Add transient ten-heart blocks, world references and actions, bounded repeats and player displays.
- Java 25 clean build passed 348 tests. The packaged validator accepted 19 wiki scripts. MCP discovery, legacy hashes, SQLite packaging and the site build passed.
- Paper 26.3 build 245 passed 25 checks through a translated loopback client. The test observed protection, reload cleanup, nine retained blocks and the tenth break, a real world write, display packets, timed repeats and revocation cleanup.

## [0.0.21] - 2026-11-21

- Add immutable typed item descriptors, player storage-inventory checks and all-or-none give/remove operations with explicit capability gates.
- Add bounded kit claims with per-feature/player/key durable cooldowns, capacity preflight and compensation when delivery fails. SQLite/inventory crash atomicity remains limited as documented.
- Add the daily-kit example, machine-readable signatures and human/AI authoring guidance.
- Java 25 clean build passed 346 tests. Packaged AI authoring, wiki examples, MCP stdio, SQLite packaging, legacy IR and documentation checks passed.
- Paper 26.3 build 245 passed 14 checks with translated loopback clients across two boots. A full inventory preserved the claim, exact rewards arrived after freeing space, repeat claims left counts unchanged, a second player claimed independently and the cooldown survived restart.

## [0.0.20] - 2026-11-21

- Add native synchronous player join/quit and block break/place handlers with typed immutable event snapshots, explicit permission gates and bounded pure evaluation.
- Add block cancellation and revision-owned event subscriptions; preserve prior cancellation by other plugins and keep delayed/host effects outside synchronous handlers.
- Add an opt-in starter-protection example, machine-readable event contract and human/AI authoring guides.
- Java 25 clean build passed 338 tests. Packaged MCP and CLI checks verified the event example, required capabilities and invalid-cancellation diagnostics. All 15 wiki examples, legacy IR checks, SQLite packaging and site checks passed.
- Paper 26.3 build 245 passed 14 checks through a translated loopback client. The join handler reported the world key, guest actions left blocks unchanged, the builder bypass permitted edits and reload delivered the replacement handler once. Quit delivery was covered by automated tests. Actual AI-client acceptance is tracked for 0.0.30.

## [0.0.19] - 2026-11-20

- Add typed per-player storage with `state_save(key, value)` and `state_load(key, fallback)`, including immutable nested maps, sets and lists.
- Preserve feature/player isolation, bounded worker execution, revision conflicts and the existing location-call API. Missing keys return the typed fallback; stored type mismatches fail explicitly.
- Add a runnable player-progress example and update the machine-readable contract, human/AI guides and persistence roadmap.
- Java 25 clean build passed all 310 tests. Packaged CLI/MCP discovery and diagnostics, SQLite packaging, seven legacy IR hashes, and all 12 wiki examples passed.
- Paper 26.3 build 245 passed 27 checks across two boots. The first player saved a map, a second player saw the missing-key fallback, and both map entries survived restart for the first player. Used two loopback 26.1 protocol clients through ViaProxy. Actual AI app and two-PC testing remain unverified for this release.

## [0.0.18] - 2026-11-20

- Add bounded immutable typed maps and sets, with deterministic iteration, typed lookup, and explicit updates.
- Bind compiler instances to a frozen catalogue of typed provider callables.
- Document canonical collection syntax and errors in the machine-readable authoring contract.

- Verified 302 Java tests, packaged MCP authoring, SQLite packaging, historical IR compatibility, wiki examples, and the documentation build.

## [0.0.17] - 2026-11-17

- Connect the supported native language calls to fresh browser-approved MCP grants. AI can discover capabilities, validate, create and edit native scripts and activate them within all-script or selected-ID permissions. Persist execution approval for new native features; explicit console ceilings remain authoritative.
- Let the owner approve one offered online player for real tests. Return bounded asynchronous test jobs with exact messages, source-linked errors, durable-save acknowledgments and actual post-teleport locations. Isolate jobs by connection/feature and cancel retired work. Preserve synthetic any-sender message tests when no actor is approved.
- Keep old grants message-only, prohibit actor/capability changes by relay replay, and preserve revocation across restart. Expose stable approval/stale/test errors and preserve an accepted test ID if its completion audit fails. Bound retained jobs and JSON capture, with explicit truncation flags.
- Simplify hosted setup with one MCP URL, app-specific instructions, endpoint copy and Cursor install links. Keep the local gateway/Windows helper optional. Document Claude automatic registration and current client setup sources; published-identity CIMD and actual-client certification remain unverified/unsupported as stated in the guides.
- Publish hosted aether_capabilities and aether_scripts_test_status alongside gateway support. Update human/AI guides and the AI-first language roadmap without expanding future capabilities into current claims.
- Verified Java 25 clean check/build: 286 tests, no failures/errors/skips; nine relay regressions; packaged MCP/authoring, both SQLite providers/licenses, nine wiki examples and seven published 0.0.11 semantic hashes.
- The deployed Worker and final Paper 26.3 build 245 JAR passed seven browser/OAuth/MCP workflow groups over two boots: native authoring with no manual permit, actual player state/teleport tests, restricted/cross-job denial, stale writes, permission removal, original-token restart and disconnect preserving local execution. The fixture uses 26.1 synthetic clients translated to 26.3; it does not certify Codex, Claude, ChatGPT or other actual AI apps.

## [0.0.16] - 2026-11-17

- Add closed typed UUID/location/unit/optional values, a typed host-call catalog and a bounded command invocation context with source-linked errors and revision/session ownership.
- Add the locally installed homes example: read the invoking player's location, persist feature/player-owned locations in SQLite, wait bounded server ticks and teleport the invoking player. Host effects run in order and are not transactional rollbacks.
- Add console-only per-feature capability approval, inspection and restriction. Revalidate preparation/startup/rollback against that policy; existing MCP grants remain message-only even with all-script access.
- Retire pending invocations on feature replacement, capability restriction, disconnect and shutdown; recheck command permission before platform effects. Accepted state commits may finish after cancellation.
- Expand the future BetterModel integration plan and registration inventory, keeping supplied upstream identifiers distinct from implemented AetherScript syntax. Document original implementation and dependency-license requirements.
- Update human/AI language and security guides, generated exports and callable discovery. Add contract/permission/isolation regression tests and a translated-client gameplay fixture.
- Verified 274 Java tests with no failures/errors/skips, eight relay regressions, packaged CLI/MCP authoring, both SQLite packages/licenses, nine wiki examples and seven published 0.0.11 semantic hashes. Paper 26.3 build 245 passed 23 homes checks across two boots with two translated protocol clients, including same-UUID reconnect cancellation and restart persistence. Website production build and link/search checks pass. Native-client UI, two-PC and performance verification remain open. See versioned evidence.

## [0.0.15] - 2026-11-15

- Animate the homepage cloudscape with an aurora, orbital trails, comets, pointer parallax, staggered scroll reveals, and an illustrative prompt-to-game sequence. Add a pause control and honor reduced-motion preferences.

## [0.0.14] - 2026-11-15

- Redesign the homepage around an AI-to-Paper workflow, with an Aether cloudscape and a working script example.
- Explain which scripting tools are available and which gameplay systems are planned for the broader AI-built server vision.

## [0.0.13] - 2026-11-15

- Accept Claude-style OAuth registration requests that include authorization_code and optionally refresh_token. Advertise authorization_code, the implemented grant.
- Document Claude custom connector enrollment, Codex CLI setup, and recovery from an expired or missing browser enrollment.
- Verified eight relay tests and a Java 25 clean build for the relay fix. The live Worker passed OAuth discovery, public registration, both Claude authorization callback checks, and unknown-client rejection. An authenticated live tool call still requires Paper enrollment.

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
