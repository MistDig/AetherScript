# Documentation website

AetherScript's GitHub Pages site uses VitePress 1.6.4 and Vue. The original smiling cloud anchors an ivory, navy and sky-blue editorial design. Documentation, local search, versioned AI exports and release links share the same deployment.

## Build

Use Node 24:

```text
npm ci --prefix site
npm run build --prefix site
npm run check --prefix site
```

The development and production preview servers bind to loopback. The deployment base is `/AetherScript/`. Restart the preview server after rebuilding: its cached HTML can otherwise reference assets removed by the new build.

## Sources

- `.vitepress/theme/AetherHome.vue`: homepage, real contract example, clipboard feedback, native capability accordions and progressive scroll reveals.
- `.vitepress/theme/AiDocsButton.vue`: copy and download the complete versioned wiki export.
- `.vitepress/theme/custom.css`: semantic light/dark tokens, responsive composition, keyboard focus and reduced motion.
- `.vitepress/config.mts`: routes, local search and release-derived navigation.
- `scripts/sync.mjs`: generates twelve guides and complete AI exports from the wiki and authoring contract.
- `scripts/check.mjs`: checks generated routes, assets, anchors, base paths, essential SSR content, search topics and complete exports.

Do not edit generated guides, copied mascot or `.vitepress/dist/`. Edit wiki sources instead. Historical exports remain immutable.

## Design and motion

The homepage uses an asymmetric typographic hero, a sky arch with the existing cloud, editorial feature rows, a copyable complete script and native expandable capability answers. The example session is explicitly illustrative. Gameplay features still on the roadmap are labelled planned. No fabricated live server state, testimonials, counts or AI responses appear.

Native CSS handles short entrance and interaction transitions; IntersectionObserver triggers one-time section reveals. Content remains visible without JavaScript or motion. Reduced motion disables transitions and entrances. No perpetual animation, smooth-scroll controller, pointer tracking or WebGL is required. Inter is already bundled locally by VitePress; the italic accent uses system Georgia. Images reserve dimensions and provide an accessible fallback.

The supplied motion brief's twelve linked repositories were reviewed before implementation: Motion, GSAP, React Three Fiber, Drei, Rive React, Lottie Web, Lenis, shadcn/ui, Radix Primitives, Lucide, Embla and Playwright. Vue-compatible Motion or GSAP would be suitable for more complex future motion, but native CSS meets this page's needs without adding runtime dependencies. The project does not bundle code or assets from those libraries. Existing Playwright is used for headless browser QA, without computer-use automation.

Vite is pinned through the existing 6.4.6 override. Recheck production builds, generated links and audit results when upgrading VitePress.

## Verification

`npm run check --prefix site` verifies all fifteen generated pages, local targets, search queries and full wiki exports. Browser verification additionally covers desktop, tablet, 390px and 320px layouts; actual clipboard actions; search; guide navigation; keyboard skip link and accordion activation; theme switching; reduced-motion styles; and the missing-mascot fallback. Screenshots and the browser run record are saved in the workspace's `work/design-qa/` directory. These local checks do not measure field Core Web Vitals or guarantee results on every browser/device.
