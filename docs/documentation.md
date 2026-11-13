# Documentation sources and release exports

Edit maintained Markdown in `docs/wiki/` for human guides and the MCP reference, `docs/ai-reference.md` for the concise AI contract, and `README.md`, `docs/roadmap.md`, `docs/architecture.md` and `docs/testing.md` for project-wide claims. Keep verification statements linked to their original version under `docs/evidence/`.

`gradle.properties` supplies the current release version. `npm run build --prefix site` runs the site synchronization and production build. `site/scripts/sync.mjs` derives formatted guide pages, search inputs, AI downloads and the changelog from these sources. Do not fix generated pages directly.

Run `python integration-tests/wiki_examples.py`, `python integration-tests/docs_consistency.py` and `npm run check --prefix site` after regeneration. The consistency check guards the remote-operation, execution-mode and verification contradictions corrected in 0.0.10.

Published `docs/ai-releases/` snapshots, historical changelog sections and recorded evidence retain their original version and limitations. Update only the current unpublished export. Follow `docs/releasing.md` to publish corrections as a new patch release.

