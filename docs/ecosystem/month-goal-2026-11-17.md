# AetherScript language milestone through December 17

The goal is to build complete Paper plugins from reusable typed APIs and extend those APIs with an addon SDK. AI authoring uses canonical syntax, machine-readable contracts, runnable examples, and precise diagnostics. Expanded MCP integration follows the language milestones.

| Order | Deliverable | Acceptance example |
| --- | --- | --- |
| 1 | Typed collections, records, packages, compiler extension contracts and general persistence | A multi-file script restores saved records after reload and restart. |
| 2 | Owned commands, cancellable events, tasks, items and inventories | Kits enforce cooldowns and capacity. Ten-heart blocks respect protection and clean up displays. |
| 3 | GUI sessions, regions, combat, teams and durable operations | A shop rejects duplicate purchases, a quest awards once, and simultaneous arenas remain isolated. |
| 4 | External addon SDK, lifecycle tests and profiling | A third-party addon registers typed operations, then unloads without leaving listeners or tasks. |
| 5 | MCP discovery, authoring and testing against verified capabilities | AI creates and tests the example plugins within approved permissions. |

The first slice implements immutable maps and sets and a frozen compiler callable catalogue. Package loading, event handlers, general persistence and live addon loading remain upcoming work. Specialized packet and proxy plugins need separate adapters and compatibility checks.

Platform references for design and future verification include [Paper events](https://docs.papermc.io/paper/dev/event-listeners/), [Paper scheduling](https://docs.papermc.io/paper/dev/scheduler/), [persistent data](https://docs.papermc.io/paper/dev/pdc/) and [WorldGuard protection queries](https://worldguard.enginehub.org/en/latest/developer/regions/protection-query/).
