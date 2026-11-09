# Compatibility verification — 2026-11-09 (Asia/Calcutta)

| Component | Pin / result |
|---|---|
| Minecraft / Paper | 26.3 / build 245, stable, commit `cd1c3b630424e7db58eeaf48d70be0c1f97d1383` |
| Paper runtime report | `26.3-245-cd1c3b6 (MC: 26.3)` |
| Paper server SHA-256 | `192d277ea1ce29b2df3933dd488521d4555cc82be864ea8df811fe536e8aed43`; downloaded file verified |
| Paper API | `io.papermc.paper:paper-api:26.3.build.245-stable` |
| API JAR SHA-256 | `3ba4fbcd676d73125acb9e4c2eb7f3cdb0080d90765dc8d3726aa09a829e6859` |
| Plugin metadata | `api-version: '26.3'`; official docs and source agree; real server accepted |
| JDK used | Oracle Java 25.0.2+10-LTS-69, Windows amd64 |
| Toolchain / bytecode | Java 25 |
| Gradle | 9.1.0 |
| Gradle distribution SHA-256 | `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806` |
| Purpur | 26.3 build 2657 discovered, **not downloaded or tested** |
| Purpur API-reported MD5 | `92c4e3ce94e6413c2cc9b1bb5918159b`; not independently verified; no SHA-256 claim |
| Player client | No connected Minecraft player test performed |

Paper download: https://fill-data.papermc.io/v1/objects/192d277ea1ce29b2df3933dd488521d4555cc82be864ea8df811fe536e8aed43/paper-26.3-245.jar

Official metadata: https://fill.papermc.io/v3/projects/paper/versions/26.3/builds/245

Purpur origin (not downloaded): https://api.purpurmc.org/v2/purpur/26.3/2657/download

API repository: https://repo.papermc.io/repository/maven-public/

## Public API decisions

Use the Bukkit `plugin.yml` static command and `JavaPlugin#getCommand().setExecutor`, not internal command maps. One `/ais` dispatcher survives script registry swaps. No dynamic alias registration, removal, or client command-tree refresh is attempted; `/helloai` is deliberately absent. The dynamic-alias experiment and release gate remain **not run**.

`BukkitScheduler#runTask` moves activation and sender interactions to the main server thread. Parsing, source reads, and audit flushes use a single bounded executor. `Component.text` avoids interpretation of markup. Disable cancels plugin tasks and retires the registry. Real-server tests cover startup, command dispatch, script reload, repeated reload, and normal shutdown, not Folia or plugin unloading.

Events and inventory/GUI lifecycle are deferred; no claim is made that those proposed adapters have been tested. Before adding them, verify listeners, async-event constraints, inventory identity/cancellation, and stale-session cleanup on the pinned API.

References checked before platform coding:

- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/dev/plugin-yml/
- https://raw.githubusercontent.com/PaperMC/Paper/main/gradle.properties
- https://docs.papermc.io/paper/dev/scheduler/
- https://docs.papermc.io/paper/dev/command-api/basics/registration/
- https://docs.papermc.io/paper/dev/event-listeners/

The moving docs establish API direction; build metadata, downloaded hashes, dependency verification, and smoke evidence pin what was actually tested.
