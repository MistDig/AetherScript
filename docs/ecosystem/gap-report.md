# M0 gap report

This is a dated ecosystem inventory. Its implementation statuses describe the recorded checkpoint. For current runtime, connectivity and verification status, use the maintained README, language guide, MCP reference and docs/testing.md.

Audit date is 11 November 2026. The baseline source is the 0.0.3 website checkpoint and the 0.0.2 engine behavior preserved by that release. The 0.0.4 checkpoint supplied the first preparation-time textual-language slice, with 113 passing combined tests and seven packaged offline authoring checks. The final binary passed 28 real Paper checks across five boots, including 100 distinct textual reloads.

## Existing foundation

| Baseline claim | Source inspected | Existing evidence | Boundary |
| --- | --- | --- | --- |
| Strict YAML/JSON parsing into one immutable feature model | [FeatureParser](../../core/src/main/java/dev/aetherscript/core/FeatureParser.java), [Feature](../../core/src/main/java/dev/aetherscript/core/Feature.java) | [Engine tests](../../core/src/test/java/dev/aetherscript/core/EngineTest.java), [0.0.3 XML](../evidence/0.0.3/TEST-dev.aetherscript.core.EngineTest.xml) | Exactly one command definition and 1–32 sender-only plain messages |
| Permission-checked invocation, stale-generation retirement and immutable replacement | [Registry](../../core/src/main/java/dev/aetherscript/core/Registry.java) | `permissionsAndSenderRestrictionsApplyAtInvocation`, `revocationAndRetirementBlockRemainingEffects`, `snapshotsImmutableAndStaleReplacementDenied` in EngineTest | Runtime values and continuations require new IR/runtime work |
| Validate/prepare before activation and preserve working edits | [FeatureManager](../../core/src/main/java/dev/aetherscript/core/FeatureManager.java), [Paper adapter](../../plugin-paper/src/main/java/dev/aetherscript/paper/AetherScriptPlugin.java) | `invalidReloadPreservesGenerationAndBehavior`, `validateHasNoPersistentSideEffectsAndInvalidReloadPreservesSnapshot`, [Paper checks](../evidence/0.0.2/paper-26.3-245-smoke.json) | The activation-to-save window can dispatch effects before a later compensation |
| Saved revisions, startup loading/recovery and held local rollback | [RevisionStore](../../core/src/main/java/dev/aetherscript/core/RevisionStore.java) | [Recovery tests](../../core/src/test/java/dev/aetherscript/core/RecoveryTest.java), [0.0.2 summary](../evidence/0.0.2/summary.json) | Default five revisions, configurable 1–50. Directory flush support depends on filesystem |
| Field diagnostics and running-versus-editable status/diff | [ValidationException](../../core/src/main/java/dev/aetherscript/core/ValidationException.java), FeatureParser, FeatureManager | `yamlAndJsonDiagnosticsCarryFieldLocationAndFix`, `statusHistoryAndDiffIdentifyOneEditTarget` in RecoveryTest | Runtime stack traces await runtime call frames |
| Main-thread activation/effects and bounded background I/O | [Paper adapter](../../plugin-paper/src/main/java/dev/aetherscript/paper/AetherScriptPlugin.java) | Real Paper console reload/restart checks in 0.0.2 | Handler latency, allocation and active-resource stress benchmarks remain open |
| Offline read-only MCP with shared parser and structured errors | [MCP Main](../../gateway-mcp/src/main/java/dev/aetherscript/mcp/Main.java), [ProtocolTest](../../gateway-mcp/src/test/java/dev/aetherscript/mcp/ProtocolTest.java) | [0.0.3 packaged subprocess](../evidence/0.0.3/mcp-stdio-process.json) | Live connection and named-client interoperability remain future gates |
| Searchable documentation website build | [Site source](../../site/README.md) | [0.0.3 site checks](../evidence/0.0.3/site-check.txt) | Build/link/search verification is recorded separately from interactive visual checks |

The stored 0.0.2 summary records 70 passing automated tests and 28 real Paper console checks across five boots and 100 distinct reloads. The 0.0.3 evidence repeats 70 automated tests and packaged MCP verification. Earlier in-game reload/error/permission checks were user-reported on 0.0.1. The audited evidence provides no connected-player automation for the new textual frontend.

## Reconciliation with the brief

The brief's starting README described 0.0.1. Durable source storage, automatic startup activation, recovery, history, bounded diff and local rollback arrived in 0.0.2. The 0.0.3 checkpoint adds the website. These capabilities therefore remain regression requirements during language work.

At the 0.0.4 checkpoint, the static `Feature` model contained one command and immutable message strings. That checkpoint dispatched precomputed strings. Current commands with declared arguments use a separate bounded runtime program. This makes preparation-time typed expressions a compatible first frontend. Per-invocation mutable locals, script state, persistent variables, loops, functions, server values and continuations require additional semantic/runtime representations. The full M1 gate remains open after a preparation-only slice.

The plugin registers one `/ais` command through `plugin.yml`. `commands.name` is metadata checked for collisions. Real script command trees, aliases and typed arguments require a new registration/lifecycle adapter. The engine currently supplies no public gameplay event, timer, inventory, entity, world, economy or addon-registration APIs.

Persistent source revisions and persistent gameplay variables have different data models. `RevisionStore` stores source bytes and active history pointers. M2 must add typed live gameplay values, per-key revisions, change hooks, namespaced identities and a declared database flush boundary. ADD-11 relies on that service for conflict-safe administration.

The existing capability surface is the parser's `runtime.message.send` allowlist plus invocation/admin permissions. A public SDK must add typed registration metadata, lifecycle ownership, version ranges and provider handling. Extending parser syntax alone does not meet the external-addon gate.

## Platform and provider matrix

| Component | Verified pin or scope | Evidence and next task |
| --- | --- | --- |
| Paper/Minecraft | Paper 26.3 build 245, commit `cd1c3b630424e7db58eeaf48d70be0c1f97d1383` | [Platform record](../compatibility.md) and 0.0.2 Paper evidence. Repeat tests for each new adapter |
| Paper API | `io.papermc.paper:paper-api:26.3.build.245-stable` | Dependency verification and existing plugin compilation |
| Java/build | Java 25, Oracle 25.0.2 local runtime, Gradle 9.1.0 | Versioned build evidence. New provider bytecode/runtime constraints need review |
| Purpur | Separate compatibility verification pending | Existing record identified a build without an executed server test |
| Folia | Dedicated scheduler/entity-region ownership adapter pending | A successful Paper build supplies no Folia lifecycle evidence |
| Skript source import/ABI | Separate project scope | `.sk` translation and addon binary compatibility require their own implementation and test corpora |
| First-wave addon references | Exact metadata tags captured for ten repositories | [Research snapshot](upstream-metadata.json). Public API, license detail and Paper 26.3 adapter behavior require per-provider investigation |
| Variable explorer reference | Upstream listed version `1.2.4-beta`, Modrinth version ID `FyzHi2w3` | Upstream lists Paper/26.3 among supported values. AetherScript's transport, codecs and staged apply have separate gates |
| Extended integrations | Provider selection and exact versions pending | Each EXT row records a concrete operation and reliability fixture before any support claim |
| Client-visible behavior | Existing plain message behavior only | Rich components, GUIs, virtual entities and resource-pack behavior need connected-client tests |

## M0 checkpoint

Source claims, existing evidence and missing capabilities are tracked in the matrix. The first implementation target is preparation-time immutable locals, typed expressions, conditions and source diagnostics through `.aether`, sharing the existing message IR. Complete M1 then requires invocation-time state/control flow, functions and SDK contracts. M2 follows with real commands, events, scheduling, typed player/location/item values and persistence before the homes/warps acceptance example.

The six preparation-language rows have reached their unit/lifecycle and packaged authoring verification checkpoint. [0.0.4 evidence](../evidence/0.0.4/) supplies the exact test records. The earlier 28 real Paper checks cited above remain attributed to 0.0.2.

The completed 0.0.4 server record is [Paper 26.3 build 245 evidence](../evidence/0.0.4/paper-26.3-245.json).
