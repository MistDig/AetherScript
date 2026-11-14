# 0.0.11 foundation evidence

Java 25 clean check build passed 234 tests with zero failures/errors. Seven relay tests passed. The packaged CLI/MCP and seven wiki examples are checked separately. Site/link/search results are recorded alongside this file.

The final plugin JAR ran on Paper 26.3 build 245. The runtime/command fixture passed 18 checks across two boots, including two argument lists on one revision, quoted input, unchanged reload, collision rejection, rename/removal and restart. Recovery passed 28 checks across five boots and 100 distinct changed reloads.

An initial live run exposed Paper's command-map entry iterator rejecting removal; implementation and forwarding-map regression tests were corrected before the final runs. An initial relay run was denied sandbox loopback access; all seven passed with loopback enabled. Tests remain console/process fixtures, not connected-player GUI/tree/suggestion verification, client certification, two-PC Feather or performance benchmarks. PersistentVariables is a core JSON service without script bindings or SQLite.
