# 0.0.12 list/default/SQLite evidence

Java 25 clean check build passed 254 tests. Actual SQLite tests cover restart, scoped typed data, competing-connection CAS, invalid batch rejection, storage limits and unsupported/corrupt schemas. Both shaded JARs passed native SQLite restart/CAS checks and retained provider license checks.

The packaged CLI/MCP verifies list iteration, invalid mixed append, function defaults/overrides and the earlier message/control examples. Nine runnable wiki examples pass. Seven semantic hashes match a checksum-verified published 0.0.11 validator; its provenance and source corpus are tracked in integration-tests/fixtures/legacy-ir-v011.json.

Paper 26.3 build 245 passed 19 runtime/command checks over two boots using typed list parameters, append/iteration and a defaulted function parameter. Checks cover two invocation values, unchanged reload, invalid list edits, collisions, rename/removal and restart. This run did not repeat 0.0.11's five-boot/100-reload recovery fixture. Console evidence is not connected-player command-tree/suggestion, GUI, client certification, two-PC Feather or performance evidence. SQLite remains a host service without script bindings or automatic migration.
