# Development scope addendum - 15 November 2026

The latest supplied research leaves the [core implementation brief](implementation-brief.md) unchanged and expands the optional BetterModel work under EXT-10. The [maintained integration plan](model-integration-plan.md) is the public specification in AetherScript's own terms.

The addition covers provider types and instance ownership, bones and hitboxes, mutable damage/click events, animation markers, player rigs, per-viewer rendering, paths, spawn rules, formulas, optional Citizens support and cleanup. Its first integration fixture is a modeled boss with head/body damage differences and a named attack marker.

The supplied research reports static inspection of SkBetterModels 0.2.0-alpha rather than a server compatibility test. It identifies 124 registration entries across 123 classes. The [reference inventory](skbettermodels-registration.csv) records identifiers/categories and planned status, not copied upstream syntax, code or documentation. The eight described type families are tracked separately in the integration plan.

The full attachment, including its verbatim upstream syntax reference, is retained locally outside the published repository. Its SHA-256 is 53f5b2c12bbd4f677198b3bd60f4c6dbe439586a82dc993d9b1a3e1c982d7cc3. That provenance is not a copyright license or evidence of runtime compatibility.

AetherScript will independently implement its public capabilities and examples. The planned adapter uses BetterModel's documented API directly, with separately reviewed dependency terms, and does not require Skript or SkBetterModels. Exact upstream syntax import and addon binary compatibility remain separate projects.
