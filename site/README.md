# Documentation website

AetherScript's GitHub Pages site uses VitePress 1.6.4, matching the stable documentation framework identified on the owner's MistTags site. The custom homepage takes visual direction from TicketPilot's blue gradients, centered hero, rounded panels and light surfaces; no TicketPilot artwork, testimonials or marketing claims are copied. The original AetherScript cloud is retained.

## Build

Use Node 24:

```text
npm ci --prefix site
npm run build --prefix site
npm run check --prefix site
```

`npm run dev --prefix site` binds only to loopback. `npm run preview --prefix site` previews the production build. The public base is `/AetherScript/`; do not deploy it at a different path without changing configuration and checks.

## Sources

- `.vitepress/theme/AetherHome.vue`: original homepage and copy-example interaction.
- `.vitepress/theme/custom.css`: light/dark tokens, responsive layouts, focus and reduced-motion behavior.
- `.vitepress/config.mts`: navigation, local search and deployment base.
- `scripts/sync.mjs`: generates nine guides from `../docs/wiki/`, copies the mascot, and reads the release version from `gradle.properties`.
- `scripts/check.mjs`: checks generated routes/assets/anchors, repository base paths, essential SSR content and search-index topics.
- `.github/workflows/docs.yml`: builds/checks pull requests; main builds deploy through GitHub Pages.

Do not edit generated `guide/`, `changelog.md`, copied mascot, or `.vitepress/dist/`. Update the wiki Markdown instead. The recovery testing page preserves its historical 0.0.2 evidence and identifies the current release separately.

## Design and accessibility

The homepage uses a saturated blue sky, white display type, charcoal primary buttons, pale blue panels and the existing clay-like cloud. Documentation uses VitePress's readable content layout with an independent sidebar and table of contents. Inter is bundled locally by VitePress. Both light/dark tokens are defined. Small screens use a single-column layout, and code blocks scroll within their own panel. Native links/buttons, descriptive image alternatives, visible focus, live copy feedback and reduced-motion styles are retained. No analytics, external font requests, fake testimonials, or simulated live server dashboard is included. The illustrated console is explicitly an example session.

## Dependency decision

VitePress 1.6.4 otherwise resolves an affected Vite 5 toolchain. The lockfile overrides Vite to **6.4.6**, supported by the installed Vue plugin's peer range, and the production build is verified with this combination. `npm audit` reported zero findings after this change. This is a tested local override, not an upstream VitePress support claim. Recheck build, generated links, and audit on upgrades.

No computer-use/browser automation is required for the build checks. They are not a claim of interactive browser or pixel-level visual verification.

References: [MistTags](https://mistdig.github.io/MistTags/), [TicketPilot](https://www.ticketpilot.me/), [VitePress 1.x deployment](https://vuejs.github.io/vitepress/v1/guide/deploy).
