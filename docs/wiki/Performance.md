[☁ Home](https://github.com/MistDig/AetherScript/wiki/Home) · [Start](https://github.com/MistDig/AetherScript/wiki/Getting-Started) · [Commands](https://github.com/MistDig/AetherScript/wiki/Commands) · [Download](https://github.com/MistDig/AetherScript/releases/tag/v0.0.2)

---

## Fast means measured, bounded work

AetherScript shares Paper's JVM garbage collector. It cannot remove GC selectively and does not force collection or change server GC flags.

- Parsing/compilation happens at startup, validation and reload, not each invocation.
- Source reads, revision writes and audit flushes use a bounded worker.
- Immutable registry swaps and message delivery run on Paper's main thread.
- Actions, source size, histories, directory entries and queues are bounded.
- There is no per-tick script polling loop or automatic watcher.

Messages still allocate components, and reload creates objects. GC-free, zero-allocation or zero-lag claims would be inaccurate.

### Will recovery make startup heavy?

Editable files already need reading and validation. Recovery adds bounded metadata inspection and saved-source reads when needed, rather than loading every historical version. It runs in the background; readiness waits for preparation. Default history is five versions and active features are capped at 128. Large-server startup latency has not been benchmarked.

### How Rust differs

Rust normally releases owned memory deterministically without a tracing collector. That can offer memory-lifetime control, but does not automatically make every program faster. A Rust component would not remove Paper's Java GC and would add a native integration boundary.

The immediate work is keeping invocation small and profiling realistic workloads: allocations, retained heap, GC pauses, invocation latency and p95/p99 tick time. The 100-reload test is a behavior test, not a leak or TPS benchmark.

[Performance policy](https://github.com/MistDig/AetherScript/blob/v0.0.2/docs/performance.md)

---

[Back to the field guide](https://github.com/MistDig/AetherScript/wiki/Home) · AetherScript 0.0.2
