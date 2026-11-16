# BetterModel capability plan - EXT-10

Updated 15 November 2026 from the [development scope addendum](development-handoff-2026-11-15.md). The original [core brief](implementation-brief.md) is unchanged. The appendix refines EXT-10 rather than replacing the M1-M3 halfway goal.

## Evidence boundary

The supplied index contains 124 registration entries across 123 unique classes: 8 conditions, 37 effect registrations, 25 events, 50 expressions and 4 sections. EffSetTrackerOffset has two registrations. This is not 124 implemented AetherScript features; one registration may include multiple patterns. The appendix also describes eight provider types separately.

The supplied audit identifies SkBetterModels 0.2.0-alpha, Modrinth version mLmiANcB, and an artifact SHA-512. It reports static bytecode/descriptor inspection, not server execution. Its build pins (Paper API 1.21.11, BetterModel 3.4.1, Skript 2.16.1 and Citizens 2.0.35-SNAPSHOT) do not establish compatibility with AetherScript's Paper 26.3 build 245. Preserve that distinction in every support claim.

The upstream BetterModel [API examples](https://github.com/toxicity188/BetterModel/wiki/API-example) were inspected for the definition/renderer versus active-tracker architecture and optional Bukkit adapter. The author documentation and Modrinth version page could not be retrieved through the web tool in this review. The supplied binary audit and checksum have not been independently reproduced here. Pin a chosen immutable provider revision/release, verify artifact hashes/licenses and execute version-specific fixtures before implementation is labeled verified. Moving v3 branch links are discovery references, not dependency pins.

The [registration inventory](skbettermodels-registration.csv) records upstream identifiers and categories without reproducing the verbatim syntax index. Raw reference material is retained locally outside the published repository. All entries start as specified; none is marked implemented or compatible with AetherScript. Capability parity uses native Aether syntax/API, not automatic .sk source or addon JAR compatibility. The planned adapter targets BetterModel directly; it must not require Skript or SkBetterModels to run.

## Dependency changes to the current plan

| Phase | Addition required by the appendix | Completion boundary |
| --- | --- | --- |
| M1 | Shared typed values/callable metadata must accommodate future provider types, optionals, finite numeric/vector/transform values and explicit precision/error semantics. Keep raw Java/provider objects outside restricted scripts. | Initial scalar/location/list types are not the complete addon SDK. |
| M2 | Add a synchronous, bounded event execution profile before cancellable combat/hitbox callbacks. Delay/async continuations cannot later cancel an expired event. Re-resolve entity identities after waits. | The current virtual-thread command profile alone cannot certify synchronous event mutation. |
| M2-M3 | Establish revision-owned tasks/callbacks/entities, per-viewer state, resource cleanup and timing budgets. Add typed damage/context semantics and native boss/effect fixtures. | These are reusable engine foundations, not BetterModel support. |
| M4 | Extend the public SDK with registered opaque provider handles, codecs/editor metadata, optional dependencies, provider epochs and typed events. Missing BetterModel or Citizens must leave core scripts working. | An external sample addon must use public SDK interfaces; no provider-specific branches in the core language. |
| M5 | Implement the optional BetterModel adapter in the vertical slices below. Detect Citizens separately. | Every claimed subset needs exact provider/Paper/Java versions and server/client evidence. |
| M6 | Stress reload/reconnect/provider-reload behavior with clients, active animations and resource counters. | Console/unit tests alone cannot prove rendering, timing, leak or two-client behavior. |

The M1-M3 halfway goal is retained. M3's native custom-boss foundation should prepare the combat/event/effect interfaces; the provider-backed boss below belongs to the optional integration phases. The new appendix expands the concrete later workload without declaring a percentage of parity complete.

## API and ownership rules

- Distinguish model definitions, general trackers, entity trackers, rendered bones, animation definitions, active playback, modifiers and player limbs. Dummy trackers are location-based instances; entity-only operations require an entity tracker.
- Spawn/attach returns an explicit owned handle. Do not reproduce a global last-created-tracker variable, which can identify the wrong instance when handlers interleave.
- Provider handles must be opaque and checked against feature revision, resource identity, validity and provider epoch. A provider reload invalidates or explicitly rebinds handles; it never silently reuses stale native objects. Design registered handle types rather than adding one core enum value for every integration.
- Keep definitions distinct from live instances, shared appearance distinct from per-viewer visibility, and a viewer despawn distinct from global tracker closure. Serialize stable identities/supported snapshots only; live handles are nonpersistent unless an explicit codec/rebind policy exists.
- Route scheduling through the engine. Retire stale listeners, marker callbacks, paths, pending attachments, viewer overrides, rigs and timers. Restore player appearance and release only resources owned by the retiring script.
- Synchronous damage/click changes execute within the real provider callback. Marker notifications are noncancellable. Thread context, legal mutations, coordinate spaces and timing units are metadata, not assumptions.
- Capability scopes remain explicit. Existing message-only hosted grants gain no model, combat, packet, filesystem, Java or general server authority from this adapter.

## Ordered integration slices

| ID | Deliverable | Acceptance fixture |
| --- | --- | --- |
| EXT-10-BM-01 | Pin provider API/artifacts, version/license matrix, optional detection and pack prerequisites. | Missing/unsupported provider fails the dependent script before activation; core scripts still run. |
| EXT-10-BM-02 | Typed definition lookup/listing and explicit entity/dummy instance handles. | Two simultaneous spawns return their own tracker; removed/expired handles fail precisely. |
| EXT-10-BM-03 | Bone lookup/enumeration/transforms and validity, with documented spaces/units. | Head/body/saddle bones resolve on the correct instance; absent bone is explicit. |
| EXT-10-BM-04 | Bone damage/click contexts and legal synchronous mutation/cancellation. | Damage override/priority test below, environmental attacker optional, projectile attribution and separate saddle click. |
| EXT-10-BM-05 | Playback definitions/active instances/modifiers, start/end/signal/marker events. | Speed changes, interruption and two bosses preserve correct playback identity and marker order. |
| EXT-10-BM-06 | Viewer-specific visibility and shared rendering: state/parts/tint/glow/brightness/scale/billboard/range/offset. | A hidden from A remains visible to B; shared part changes are distinguished. |
| EXT-10-BM-07 | Player rigs, disguise variants, limbs and first-person arms/animation. | Wearer and observer see expected distinct results; quit/reload restores appearance. |
| EXT-10-BM-08 | Bone visuals including timed flashes, descendants and overlapping effects. | Nested bones and overlapping flash lifetimes restore the correct prior state. |
| EXT-10-BM-09 | Body/head rotation constraints and bone look-at with limits/front offset. | Rate/angle/unit semantics verified; alpha/version-limited orientations remain labeled. |
| EXT-10-BM-10 | Bone hitbox enable/disable/bind and separately gated resize/reset. | Missing authored hitbox reports clearly; resize is independently version-tested and reset restores listeners. |
| EXT-10-BM-11 | Timed paths to fixed/moving targets, easing/wobble/progress and distinct finish/cancel. | Replacement, target removal, wrong world and cancellation clean up; no obstacle-avoidance claim without evidence. |
| EXT-10-BM-12 | Spawn rules with chance/world/reason filters, overlap precedence and cancellable attachment veto. | 0/100 percent, duplicate IDs, entity removal before attach; veto leaves native mob spawn intact. |
| EXT-10-BM-13 | Separately optional Citizens model assignment. | Missing Citizens is a dependency error only for dependent scripts; valid NPC attachment cleans up. |
| EXT-10-BM-14 | Bounded numeric/vector formulas with typed variable/bone lookup and finite results. | Missing variable/bone and nonfinite result reject safely; no unrestricted eval/Java escape. |
| EXT-10-BM-15 | Full ownership/provider-reload/resource-pack and compatibility verification. | Invalid tracker, removed model, stale pack, unsupported resize and provider reload remain isolated across scripts/viewers. |

Map every registration row to these native capabilities and record per-binding status/evidence. A generic Java reflection bridge does not complete this integration.

## First playable provider fixture

Ship a boss model/pack with head and body hitboxes, a saddle click target, an attack clip and named impact marker. The Aether script creates the instance, handles bone-specific damage, plays the attack and triggers its impact sound at the marker using only public APIs.

Damage ordering acceptance: base damage 4 with model multiplier 1.5 and entity override 2 must enter the script as 8. Subtract 1 and pass 7 back to BetterModel before later combat processing. Clear the entity override and the next identical hit enters as 6. Verify cancellation, environmental damage and direct/causing projectile attribution separately.

Marker acceptance: interrupt before impact, alter speed and run two bosses simultaneously. Record callback order, playback/entity identity and measured server delivery timing. Do not promise exact tick alignment from the marker convention alone.

Visual/lifecycle acceptance uses two clients: hide from one viewer only, inspect first-person arms versus observer disguise, disconnect and reload mid-animation, reload the provider, and verify owned-resource counts. Server fixtures, client rendering and performance evidence are separate statuses.

## Independent implementation and dependency provenance

Use upstream features and documented API contracts as references; implement AetherScript code, syntax, explanations, examples and fixture assets independently. Do not copy or translate upstream implementation code, substantial documentation, model packs, branding or artwork without verified permission and compatible license obligations. Keep attribution and required license/notice files for dependencies actually distributed. Linking a reference is not claiming endorsement or compatibility.

The current BetterModel repository advertises an MIT license, while Skript's main project is GPLv3 with contribution-specific licensing notes. Those facts do not assign a license to SkBetterModels or every addon. Verify the exact component/revision and chosen reuse/integration before importing any material. Unknown-license material stays reference-only; prefer an original implementation. These engineering controls reduce risk and do not promise immunity from claims.

References: [Skript licensing](https://github.com/SkriptLang/Skript/blob/master/LICENSING.md), [BetterModel license](https://github.com/toxicity188/BetterModel/blob/v3/LICENSE.md).
