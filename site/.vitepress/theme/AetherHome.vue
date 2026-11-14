<script setup>
import authoring from '../../../core/src/main/resources/aetherscript-authoring-v1.json'
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { withBase, useData } from 'vitepress'
const { theme } = useData()
const copyState = ref('')
const motionPaused = ref(false)
const selectedStage = ref(0)
const stages = [
  { label: 'Prompt', text: 'Give players a custom welcome command.' },
  { label: 'AetherScript', text: 'command welcome …' },
  { label: 'In Minecraft', text: '/welcome → Welcome to the server!' }
]
const imageFailed = ref(false)
const example = authoring.template
let observer
function moveSky(event) {
  if (motionPaused.value || event.pointerType === "touch" || !window.matchMedia("(prefers-reduced-motion: no-preference)").matches) return
  const scene = event.currentTarget
  const bounds = scene.getBoundingClientRect()
  scene.style.setProperty("--sky-x", ((event.clientX - bounds.left) / bounds.width - .5) * 34 + "px")
  scene.style.setProperty("--sky-y", ((event.clientY - bounds.top) / bounds.height - .5) * 26 + "px")
}
function resetSky(event) {
  event.currentTarget.style.removeProperty("--sky-x")
  event.currentTarget.style.removeProperty("--sky-y")
}
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
  { title: 'Describe the behavior', text: 'Tell your AI what the command should do, who can use it, and what players should see.', link: 'getting-started', label: 'Meet live reload' },
  { title: 'Generate and validate', text: 'AetherScript gives AI explicit types, readable rules, and source-linked errors it can use to fix a script.', link: 'diagnostics', label: 'Understand an error' },
  { title: 'Test and iterate', text: 'With your permission, AI can apply a script and check its output. Saved revisions let you review and roll back changes.', link: 'recovery', label: 'Explore recovery' }
]
</script>

<template>
<div class="aether-home">
  <section class="sky-hero" aria-labelledby="hero-title">
    <div class="hero-content">
      <a class="release-link" :href="`https://github.com/MistDig/AetherScript/releases/tag/v${theme.releaseVersion}`">AetherScript {{ theme.releaseVersion }} <span aria-hidden="true">↗︎</span></a>
      <p class="eyebrow">AI SCRIPTING FOR MINECRAFT</p>
      <h1 id="hero-title">Describe the server.<br><em>Build how it plays.</em></h1>
      <p class="hero-description">Connect your AI to Paper through MCP. Describe a feature, then have your AI write, validate and test the AetherScript that powers it.</p>
      <div class="hero-actions">
        <a class="aether-button primary" :href="withBase('/guide/security.html')">Connect your AI <span aria-hidden="true">↗︎</span></a>
        <a class="download-link" :href="`https://github.com/MistDig/AetherScript/releases/download/v${theme.releaseVersion}/AetherScript-${theme.releaseVersion}.jar`">Download plugin <span aria-hidden="true">↓</span></a>
      </div>
      <p class="hero-meta">Paper 26.3 <span>·</span> Java 25 <span>·</span> Open source</p>
    </div>
    <div class="cloud-scene" :class="{ 'motion-paused': motionPaused }" @pointermove="moveSky" @pointerleave="resetSky">
      <div class="aurora" aria-hidden="true"></div>
      <div class="flight-orbits" aria-hidden="true"><i></i><i></i><i></i></div>
      <div class="sky-comets" aria-hidden="true"><i></i><i></i></div>
      <div class="sky-rings" aria-hidden="true"></div>
      <span class="scene-caption">AI → AETHERSCRIPT → PAPER</span>
      <div class="aether-mascot" role="img" aria-label="A smiling cloud mascot">
      <div class="mascot-puffs" aria-hidden="true"><i></i><i></i><i></i></div>
      <div class="mascot-face" aria-hidden="true"><i></i><i></i><span></span></div>
    </div>
    <span class="scene-star star-one" aria-hidden="true">✦</span><span class="scene-star star-two" aria-hidden="true">✧</span>
      <div class="flight-sequence" aria-label="Illustrative prompt-to-game sequence">
        <div class="flight-output" aria-live="polite"><span :key="selectedStage">{{ stages[selectedStage].text }}</span></div>
        <button v-for="(stage, index) in stages" :key="stage.label" type="button" :aria-pressed="selectedStage === index" @click="selectedStage = index">{{ stage.label }}</button>
      </div>
      <button class="sky-motion-control" type="button" @click="motionPaused = !motionPaused">{{ motionPaused ? 'Play animation' : 'Pause animation' }}</button>
      <div class="scene-bottom"><span>AetherScript</span><span>Example prompt</span></div>
    </div>
  </section>

  <section class="principles home-section" aria-labelledby="principles-title" data-reveal>
    <div class="section-heading"><p class="section-kicker">THE AUTHORING WORKFLOW</p><h2 id="principles-title">From a prompt to a playable feature.</h2></div>
    <div class="principle-list"><a v-for="item in principles" :key="item.title" :href="withBase(`/guide/${item.link}.html`)"><h3>{{ item.title }}</h3><p>{{ item.text }}</p><span>{{ item.label }} <b aria-hidden="true">↗︎</b></span></a></div>
  </section>

  <section class="example-section home-section" aria-labelledby="example-title" data-reveal>
    <div class="example-intro"><p class="section-kicker">A WORKING SCRIPT</p><h2 id="example-title">A language built<br> for AI to read.</h2><p>One feature per file. Explicit permissions and predictable syntax help your AI generate scripts you can inspect and test.</p><ol class="workflow-list"><li><strong>Save your script</strong><span>plugins/AetherScript/scripts/cloud-note.aether</span></li><li><strong>Validate, then reload</strong><span>/ais validate cloud-note<br>/ais reload cloud-note</span></li><li><strong>Try it in game</strong><span>/ais run cloud-note</span></li></ol><a class="text-link" :href="withBase('/guide/ai-authoring.html')">Read the authoring guide <span aria-hidden="true">↗︎</span></a></div>
    <div class="code-window"><div class="window-bar"><span class="file-mark" aria-hidden="true">{ }</span><span>cloud-note.aether</span><button type="button" @click="copyExample" aria-label="Copy the complete cloud-note AetherScript example">{{ copyState.startsWith('Copied') ? 'Copied ✓' : 'Copy script' }}</button></div><pre tabindex="0" aria-label="Complete cloud-note AetherScript script"><code>{{ example }}</code></pre><div class="example-console"><span>EXAMPLE SESSION · NOT A LIVE SERVER</span><code>&gt; /ais reload cloud-note</code><p>Valid changes activate and save a revision.</p></div><p class="copy-feedback" role="status">{{ copyState }}</p></div>
  </section>

  <section class="ai-section home-section" aria-labelledby="ai-title" data-reveal><div><p class="section-kicker">YOUR TOOLS. YOUR PERMISSIONS.</p><h2 id="ai-title">Connect your AI.<br> Choose its access.</h2><p>Give your AI the complete language guide, or connect MCP through a shared relay. Choose scoped or full-native access with server-side grants.</p><a class="text-link" :href="withBase('/guide/security.html')">Connect my AI <span aria-hidden="true">↗︎</span></a></div><div class="docs-parcel"><span class="parcel-icon" aria-hidden="true">{…}</span><h3>The whole guide, to go.</h3><p>A versioned, full-text wiki export for your AI. No missing chapters.</p><AiDocsButton /></div></section>

  <section class="scope-section home-section" aria-labelledby="scope-title" data-reveal><div class="scope-heading"><p class="section-kicker">THE ROADMAP</p><h2 id="scope-title">From custom commands<br> to custom servers.</h2><a class="text-link" :href="withBase('/guide/roadmap.html')">Explore the roadmap ↗︎</a></div><div class="scope-details"><details open><summary>What can I build today?<span aria-hidden="true">+</span></summary><p>Native message commands with typed arguments and conditions, live reload, saved revisions and diagnostics. Offline validation and exact-message tests also work.</p></details><details><summary>What is still planned?<span aria-hidden="true">+</span></summary><p>The goal is to describe a whole server experience and have AI assemble it from scripts. Gameplay events, custom items, quests, economy, and persistent player data are planned.</p></details><details><summary>How is AI access controlled?<span aria-hidden="true">+</span></summary><p>Server grants protect each operation. Scoped and full-native access still enforce engine validation and server permissions. General console execution is unavailable.</p><a class="text-link" :href="withBase('/guide/security.html')">Read the security boundaries ↗︎</a></details></div></section>

  <section class="closing-section" aria-labelledby="closing-title"><p class="section-kicker">START BUILDING</p><h2 id="closing-title">What should your server do?</h2><div class="closing-links"><a :href="withBase('/guide/getting-started.html')">Getting started ↗︎</a><a :href="withBase('/guide/commands.html')">Commands & permissions ↗︎</a><a :href="withBase('/guide/testing.html')">Test your server ↗︎</a><a href="https://github.com/MistDig/AetherScript">Explore the source ↗︎</a></div></section>
</div>
</template>
