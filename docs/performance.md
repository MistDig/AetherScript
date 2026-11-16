# Performance and garbage collection

AetherScript is a Java plugin inside Paper's JVM. It does not have a separate garbage collector; Paper, AetherScript, and other Java plugins share JVM-managed memory. The JVM garbage collector cannot be selectively removed from this plugin. Disabling reclamation would eventually exhaust the heap, rather than guarantee fast gameplay.

The supported goal is **low allocation pressure and predictable tick work**, not a claim of GC-free or zero-lag execution.

## Current behavior

- No explicit `System.gc()` or `Runtime.gc()` calls were found in the plugin, core, or gateway Java source.
- Script parsing, validation, and compilation happen on validation/reload, not on each feature invocation.
- Disk reads and audit flushes run on a bounded background executor; the JVM can still allocate/collect memory during background work.
- Invocations read an immutable registry snapshot, check permissions, and share bounded interpreter/output budgets (at most 32 messages). Locally approved host commands add guarded location/state/delay/self-teleport calls; at most 32 invocations run globally, four per player and eight per feature, with a 120-second deadline. There is no per-tick script polling loop or automatic file watcher.
- Host storage runs on the bounded I/O worker. Virtual threads wait for storage, bounded tick delays and asynchronous chunk loading; player reads/effects re-enter Paper's thread. These bounds are resource controls, not measured latency or allocation guarantees.
- Messages still create Adventure components and can allocate memory. Reloading creates new definitions/snapshots and retires old ones. Zero allocation is not claimed.
- AetherScript does not set server JVM GC flags or force collection. Server owners choose heap/collector settings for their measured workload.

## Development policy

Do not add forced GC calls or disable the server's collector. Keep parsing/compilation out of normal invocation paths and file I/O off game-thread ticks; invocation-requested storage must use bounded workers. Avoid unbounded queues, retained retired generations, and unnecessary temporary objects in frequently executed code. Preserve permission and lifecycle checks while optimizing; do not pool mutable objects without evidence that reuse is safe and beneficial.

Before claiming a performance improvement, compare the same server/JDK/hardware and workload, with a baseline and JIT warm-up. Measure allocation rate, retained heap after repeated reloads, GC pause distributions, invocation latency, and server tick time (including p95/p99). Use Java Flight Recorder or a suitable server profiler; record configuration and results. Test both normal traffic and burst/reload scenarios. If an allocation is material, consider precomputation/caching with explicit lifecycle cleanup, then measure again.

The 100-reload smoke test is a behavior check, not a GC/heap-leak or load benchmark. No allocation-rate, GC-pause, or tick-latency target has yet been measured or certified.

Reference: [Oracle JDK 25 garbage collection guide](https://docs.oracle.com/en/java/javase/25/gctuning/introduction-garbage-collection-tuning.html).
