import { defineConfig } from 'vitepress'
import { readFileSync } from 'node:fs'
const version = readFileSync(new URL('../../gradle.properties', import.meta.url), 'utf8').match(/^version=(.+)$/m)![1].trim()
export default defineConfig({
  title: 'AetherScript', description: 'Little scripts. Big possibilities. Live Minecraft scripting with saved revisions and clear diagnostics.',
  srcExclude: ['README.md'], lang: 'en-US', base: '/AetherScript/', appearance: true, lastUpdated: false,
  head: [
    ['meta', { name: 'theme-color', content: '#1479ee' }],
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/AetherScript/favicon.svg' }],
    ['meta', { property: 'og:title', content: 'AetherScript â€” your server, your little universe.' }],
    ['meta', { property: 'og:description', content: 'Build something lovely. Live script reloads, saved working versions, and a guide for every step.' }],
    ['meta', { property: 'og:image', content: 'https://mistdig.github.io/AetherScript/cloud-mascot.png' }]
  ],
  themeConfig: {
    logo: { src: '/favicon.svg', alt: '' },
    nav: [
      { text: 'Guide', link: '/guide/getting-started' },
      { text: 'Commands', link: '/guide/commands' },
      { text: 'Roadmap', link: '/guide/roadmap' },
      { text: `v${version}`, items: [
        { text: 'Download release', link: `https://github.com/MistDig/AetherScript/releases/tag/v${version}` },
        { text: 'Changelog', link: '/changelog' },
        { text: 'GitHub wiki', link: 'https://github.com/MistDig/AetherScript/wiki' }
      ] }
    ],
    sidebar: [
      { text: 'START SMALL', items: [
        { text: 'Getting started', link: '/guide/getting-started' },
        { text: 'Language & examples', link: '/guide/language' },
        { text: 'Writing scripts with AI', link: '/guide/ai-authoring' },
        { text: 'Commands & permissions', link: '/guide/commands' }
      ] },
      { text: 'KEEP IT WORKING', items: [
        { text: 'Revisions & recovery', link: '/guide/recovery' },
        { text: 'Diagnostics & diffs', link: '/guide/diagnostics' },
        { text: 'Test your server', link: '/guide/testing' }
      ] },
      { text: 'LOOK AHEAD', items: [
        { text: 'Security & MCP', link: '/guide/security' },
        { text: 'Performance', link: '/guide/performance' },
        { text: 'Roadmap', link: '/guide/roadmap' },
        { text: 'Changelog', link: '/changelog' }
      ] }
    ],
    search: { provider: 'local', options: { detailedView: true } },
    outline: { level: [2, 3], label: 'On this page' },
    socialLinks: [{ icon: 'github', link: 'https://github.com/MistDig/AetherScript' }],
    footer: { message: 'A little cloud. A careful engine. Your next idea.', copyright: `AetherScript ${version} Â· Apache-2.0 Â· Experimental` },
    // Custom data is serialized by VitePress for the homepage download button.
    releaseVersion: version
  }
})
