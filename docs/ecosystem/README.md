# Core language and addon ecosystem

This inventory reconciles the implementation brief prepared on 9 November 2026 with the repository inspected on 11 November 2026. The authorizing request is the user's implementation instruction in this chat. The brief supplies the product scope and acceptance gates.

- [Gap report and baseline evidence](gap-report.md)
- [Concrete capability matrix](capabilities.csv)
- [Dependency-ordered backlog and flagship gates](backlog.md)
- [Initial textual-language decision](language-decision.md)
- [Upstream release metadata](upstream-metadata.json)

The CSV contains 154 concrete capability rows with the requested 17 tracking fields. Its statuses describe each row's scope. `verified` rows cite existing evidence. The six initial textual frontend rows are `verified` for pure-language and lifecycle unit tests, plus packaged offline authoring checks. `specified` rows have a recorded requirement and acceptance fixture. `unresearched` rows require exact upstream/API work before implementation. Source and binary Skript compatibility are tracked as separate projects with `intentionally unsupported` status for the present engine.

Change a status only after checking the public interface, examples, relevant negative cases, lifecycle/thread behavior, dependency handling and version evidence. Unit, subprocess, real Paper and connected-player evidence are recorded at their actual levels. Version labels in evidence remain tied to their original release.

First-wave repository metadata identifies ten reference release tags and license declarations. The variable-editor record identifies an upstream listing. Their compatibility fields record the research still required before claiming an AetherScript adapter. Extended-addon names remain discovery leads until original repositories and exact releases are selected.

The 0.0.4 checkpoint has 113 passing automated tests and seven packaged offline authoring checks. The final 0.0.4 plugin passed 28 Paper console checks across five boots and 100 changed reloads. [Versioned evidence](../evidence/0.0.4/) records the executed tests.

The completed 0.0.4 server record is [Paper 26.3 build 245 evidence](../evidence/0.0.4/paper-26.3-245.json).
