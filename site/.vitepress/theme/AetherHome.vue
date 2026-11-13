<script setup>
import authoring from '../../../core/src/main/resources/aetherscript-authoring-v1.json'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { withBase, useData } from 'vitepress'
const { theme } = useData()
const copyState = ref('')
const imageFailed = ref(false)
const example = authoring.template
let observer
async function copyExample() {
  try { await navigator.clipboard.writeText(example); copyState.value = 'Copied. Save as cloud-note.aether.' }
  catch { copyState.value = 'Copy unavailable. Select the example text to copy it.' }
}
onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches || !('IntersectionObserver' in window)) return
  observer = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) { entry.target.classList.add('is-seen'); observer.unobserve(entry.target) }
  }), { threshold: .12 })
  document.querySelectorAll('.aether-home [data-reveal]').forEach(element => observer.observe(element))
})
onBeforeUnmount(() => observer?.disconnect())
const principles = [
  { title: 'Ideas move quickly.', text: 'Edit one file. Reload one feature. See your next idea in game without restarting Paper.', link: 'getting-started', label: 'Meet live reload' },
  { title: 'Mistakes have a soft landing.', text: 'Invalid edits leave the working script running. Clear diagnostics tell you where to fix the source.', link: 'diagnostics', label: 'Understand an error' },
  { title: 'Good versions stay close.', text: 'Successful changes become saved revisions. Compare what you edited with what is running, or roll back.', link: 'recovery', label: 'Explore recovery' }
]
</script>

<template>
<div class="aether-home">
  <section class="sky-hero" aria-labelledby="hero-title">
    <div class="hero-content">
      <a class="release-link" :href="`https://github.com/MistDig/AetherScript/releases/tag/v${theme.releaseVersion}`">AetherScript {{ theme.releaseVersion }} <span aria-hidden="true">↗︎</span></a>
      <p class="eyebrow">A LITTLE LANGUAGE FOR YOUR MINECRAFT SERVER</p>
      <h1 id="hero-title">Your server.<br>Your little<br><em>universe.</em></h1>
      <p class="hero-description">Turn a small idea into a working script.<br>Live reloads. Clear errors. Room to experiment.</p>
      <div class="hero-actions">
        <a class="aether-button primary" :href="withBase('/guide/getting-started.html')">Write your first script <span aria-hidden="true">↗︎</span></a>
        <a class="download-link" :href="`https://github.com/MistDig/AetherScript/releases/download/v${theme.releaseVersion}/AetherScript-${theme.releaseVersion}.jar`">Download plugin <span aria-hidden="true">↓</span></a>
      </div>
      <p class="hero-meta">Paper 26.3 <span>·</span> Java 25 <span>·</span> Open source</p>
    </div>
    <div class="cloud-scene">
      <div class="sky-rings" aria-hidden="true"></div>
      <span class="scene-caption">A SPACE FOR YOUR NEXT IDEA</span>
      <img v-if="!imageFailed" class="hero-cloud" :src="withBase('/cloud-mascot.png')" alt="A smiling white cloud with rosy cheeks" width="1536" height="1024" fetchpriority="high" @error="imageFailed = true">
      <div v-else class="cloud-fallback" role="img" aria-label="A friendly smiling cloud">☁<span>⌣</span></div>
      <span class="scene-star star-one" aria-hidden="true">✦</span><span class="scene-star star-two" aria-hidden="true">✧</span>
      <div class="scene-note"><span aria-hidden="true">↳</span> Make something that feels like you.</div>
      <div class="scene-bottom"><span>AetherScript</span><span>Small scripts. Big possibilities.</span></div>
    </div>
  </section>

  <section class="principles home-section" aria-labelledby="principles-title" data-reveal>
    <div class="section-heading"><p class="section-kicker">LESS FRICTION. MORE “WHAT IF?”</p><h2 id="principles-title">A little language.<br>A softer learning curve.</h2></div>
    <div class="principle-list"><a v-for="item in principles" :key="item.title" :href="withBase(`/guide/${item.link}.html`)"><h3>{{ item.title }}</h3><p>{{ item.text }}</p><span>{{ item.label }} <b aria-hidden="true">↗︎</b></span></a></div>
  </section>

  <section class="example-section home-section" aria-labelledby="example-title" data-reveal>
    <div class="example-intro"><p class="section-kicker">THE FIRST HELLO</p><h2 id="example-title">Start small.<br>See it happen.</h2><p>Copy this complete working example, save it, and make the message yours.</p><ol class="workflow-list"><li><strong>Save your script</strong><span>plugins/AetherScript/scripts/cloud-note.aether</span></li><li><strong>Validate, then reload</strong><span>/ais validate cloud-note<br>/ais reload cloud-note</span></li><li><strong>Try it in game</strong><span>/ais run cloud-note</span></li></ol><a class="text-link" :href="withBase('/guide/ai-authoring.html')">Read the authoring guide <span aria-hidden="true">↗︎</span></a></div>
    <div class="code-window"><div class="window-bar"><span class="file-mark" aria-hidden="true">{ }</span><span>cloud-note.aether</span><button type="button" @click="copyExample" aria-label="Copy the complete cloud-note AetherScript example">{{ copyState.startsWith('Copied') ? 'Copied ✓' : 'Copy script' }}</button></div><pre tabindex="0" aria-label="Complete cloud-note AetherScript script"><code>{{ example }}</code></pre><div class="example-console"><span>EXAMPLE SESSION · NOT A LIVE SERVER</span><code>&gt; /ais reload cloud-note</code><p>Valid changes activate and save a revision.</p></div><p class="copy-feedback" role="status">{{ copyState }}</p></div>
  </section>

  <section class="ai-section home-section" aria-labelledby="ai-title" data-reveal><div><p class="section-kicker">YOUR TOOLS. YOUR PERMISSIONS.</p><h2 id="ai-title">Bring your AI.<br>Keep the keys.</h2><p>Give your AI the complete language guide, or connect MCP through a shared relay. Choose scoped or full-native access with server-side grants.</p><a class="text-link" :href="withBase('/guide/security.html')">Connect my AI <span aria-hidden="true">↗︎</span></a></div><div class="docs-parcel"><span class="parcel-icon" aria-hidden="true">{…}</span><h3>The whole guide, to go.</h3><p>A versioned, full-text wiki export for your AI. No missing chapters.</p><AiDocsButton /></div></section>

  <section class="scope-section home-section" aria-labelledby="scope-title" data-reveal><div class="scope-heading"><p class="section-kicker">A CLEAR VIEW OF WHAT'S HERE</p><h2 id="scope-title">Built today.<br>Dreaming ahead.</h2><a class="text-link" :href="withBase('/guide/roadmap.html')">Explore the roadmap ↗︎</a></div><div class="scope-details"><details open><summary>What can I build today?<span aria-hidden="true">+</span></summary><p>Native message commands with typed arguments and conditions, live reload, saved revisions and diagnostics. Offline validation and exact-message tests also work.</p></details><details><summary>What is still planned?<span aria-hidden="true">+</span></summary><p>Gameplay events, block health, scoreboards, boss bars, custom items, cooldowns, persistent player data, economy systems and quests are on the roadmap.</p></details><details><summary>How is AI access controlled?<span aria-hidden="true">+</span></summary><p>Server grants protect each operation. Scoped and full-native access still enforce engine validation and server permissions. General console execution is unavailable.</p><a class="text-link" :href="withBase('/guide/security.html')">Read the security boundaries ↗︎</a></details></div></section>

  <section class="closing-section" aria-labelledby="closing-title"><p class="section-kicker">THE NEXT CHAPTER IS YOURS</p><h2 id="closing-title">Hello, little universe.</h2><div class="closing-links"><a :href="withBase('/guide/getting-started.html')">Getting started ↗︎</a><a :href="withBase('/guide/commands.html')">Commands & permissions ↗︎</a><a :href="withBase('/guide/testing.html')">Test your server ↗︎</a><a href="https://github.com/MistDig/AetherScript">Explore the source ↗︎</a></div></section>
</div>
</template>
