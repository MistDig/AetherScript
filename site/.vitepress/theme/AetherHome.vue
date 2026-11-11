<script setup>
import authoring from '../../../core/src/main/resources/aetherscript-authoring-v1.json'
import { ref } from 'vue'
import { withBase, useData } from 'vitepress'
const { theme } = useData()
const copyState = ref('')
const example = authoring.template
async function copyExample() {
  try { await navigator.clipboard.writeText(example); copyState.value = 'Copied. Save as cloud-note.aether.' }
  catch { copyState.value = 'Copy unavailable. Select the example text to copy it.' }
}
const features = [
  { n: '01', title: 'A new idea. Already live.', text: 'Edit your script and reload just that feature. Try the change without restarting Paper.', link: 'getting-started', label: 'Start building', icon: 'M13 2 4 14h7l-1 8 10-12h-7l1-8Z' },
  { n: '02', title: 'Room to make mistakes.', text: 'A bad edit leaves your saved working script running. Fix the file when you are ready.', link: 'recovery', label: 'Meet your safety net', icon: 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7l-9-4ZM8 12l3 3 5-6' },
  { n: '03', title: 'Less guessing. More making.', text: 'See the file, field and location of an error. Compare your edit with what is actually running.', link: 'diagnostics', label: 'Read your diagnostics', icon: 'M4 5h16M4 12h10M4 19h7M17 16l3 3-3 3' }
]
</script>

<template>
<div class="aether-home">
  <section class="sky-hero" aria-labelledby="hero-title">
    <div class="hero-grid" aria-hidden="true"></div>
    <div class="hero-content">
      <a class="release-pill" :href="`https://github.com/MistDig/AetherScript/releases/tag/v${theme.releaseVersion}`"><span class="status-dot" aria-hidden="true"></span> AetherScript {{ theme.releaseVersion }} <span class="pill-divider">/</span> Ready to explore <span aria-hidden="true">↗</span></a>
      <p class="eyebrow">SMALL SCRIPTS. ENDLESS IDEAS.</p>
      <h1 id="hero-title">Your server.<br><span>Your little universe.</span></h1>
      <p class="hero-description">Build a little magic for your Minecraft server.<br class="desktop-break"> Live reloads, saved working versions, and a guide for every step.</p>
      <div class="hero-actions">
        <a class="aether-button primary" :href="withBase('/guide/getting-started.html')">Start building <span aria-hidden="true">↗</span></a>
        <a class="aether-button glass" :href="`https://github.com/MistDig/AetherScript/releases/download/v${theme.releaseVersion}/AetherScript-${theme.releaseVersion}.jar`">Download plugin <span aria-hidden="true">↓</span></a>
      </div>
      <p class="hero-meta">Paper 26.3 <span aria-hidden="true">·</span> Java 25 <span aria-hidden="true">·</span> Open source</p>
    </div>
    <div class="cloud-scene">
      <span class="cloud-orbit orbit-one" aria-hidden="true"></span><span class="cloud-orbit orbit-two" aria-hidden="true"></span>
      <span class="spark spark-one" aria-hidden="true">✦</span><span class="spark spark-two" aria-hidden="true">✧</span>
      <img class="hero-cloud" :src="withBase('/cloud-mascot.png')" alt="A happy little cloud with rosy cheeks" width="1536" height="1024" fetchpriority="high">
      <div class="floating-note note-left"><span class="tiny-dot" aria-hidden="true"></span> hello-ai <strong>ACTIVE</strong></div>
      <div class="floating-note note-right"><span aria-hidden="true">↺</span> Your last good idea, saved.</div>
    </div>
  </section>

  <section class="home-section features-section" aria-labelledby="features-title">
    <p class="section-kicker">MAKE SOMETHING THAT FEELS LIKE YOU</p>
    <h2 id="features-title">A small script.<br><span>A lovely place to start.</span></h2>
    <p class="section-lead">Keep the creative part. Make the fiddly part a little easier.</p>
    <div class="feature-grid">
      <a v-for="feature in features" :key="feature.n" class="feature-card" :href="withBase(`/guide/${feature.link}.html`)">
        <div class="feature-top"><span class="feature-icon"><svg viewBox="0 0 24 24" aria-hidden="true"><path :d="feature.icon" /></svg></span><span class="feature-number">{{ feature.n }}</span></div>
        <h3>{{ feature.title }}</h3><p>{{ feature.text }}</p><span class="card-link">{{ feature.label }} <span aria-hidden="true">↗</span></span>
      </a>
    </div>
  </section>

  <section class="home-section example-section" aria-labelledby="example-title">
    <div class="example-intro">
      <p class="section-kicker">FROM “WHAT IF” TO “IT WORKS”</p>
      <h2 id="example-title">Your first bit<br>of server magic.</h2>
      <p>Start with a message. Make it yours. Then see it change in game with one reload.</p>
      <ol class="workflow-list"><li><span>1</span><div><strong>Write a little</strong><p>One editable file in your scripts folder.</p></div></li><li><span>2</span><div><strong>Reload your idea</strong><p>Validate it, then activate the change.</p></div></li><li><span>3</span><div><strong>Keep the good version</strong><p>Successful changes are saved for recovery.</p></div></li></ol>
      <a class="text-link" :href="withBase('/guide/ai-authoring.html')">Write and validate a script <span aria-hidden="true">→</span></a>
    </div>
    <div class="code-window">
      <div class="window-bar"><div class="window-dots" aria-hidden="true"><i></i><i></i><i></i></div><span>cloud-note.aether</span><button type="button" @click="copyExample" aria-label="Copy the complete cloud-note AetherScript example">Copy</button></div>
      <pre tabindex="0" aria-label="Complete cloud-note AetherScript script"><code>{{ example }}</code></pre>
      <div class="example-console"><span>EXAMPLE SESSION</span><code>&gt; /ais reload cloud-note</code><p><span class="tiny-dot" aria-hidden="true"></span> ACTIVE · saved successfully</p></div>
      <p class="copy-feedback" role="status" aria-live="polite">{{ copyState }}</p>
    </div>
  </section>

  <section class="home-section guide-section" aria-labelledby="guide-title">
    <div class="section-heading"><div><p class="section-kicker">A GUIDE FOR EVERY STEP</p><h2 id="guide-title">Find your next little thing.</h2></div><a class="text-link" :href="withBase('/guide/getting-started.html')">Open the guide <span aria-hidden="true">↗</span></a></div>
    <div class="guide-grid">
      <a :href="withBase('/guide/commands.html')"><span>THE EVERYDAY TOOLS</span><h3>Commands & permissions</h3><p>Know what to run, and who can run it.</p><b aria-hidden="true">↗</b></a>
      <a :href="withBase('/guide/recovery.html')"><span>YOUR SAFETY NET</span><h3>Revisions & recovery</h3><p>Find your working version. Get it back.</p><b aria-hidden="true">↗</b></a>
      <a :href="withBase('/guide/testing.html')"><span>BEFORE THE BIG REVEAL</span><h3>Test your server</h3><p>A practical checklist for your next update.</p><b aria-hidden="true">↗</b></a>
      <a :href="withBase('/guide/roadmap.html')"><span>A LITTLE FURTHER AHEAD</span><h3>What's on the horizon?</h3><p>Events, block health, UI, quests and more.</p><b aria-hidden="true">↗</b></a>
    </div>
  </section>

  <section class="home-section honest-section" aria-labelledby="scope-title"><span class="scope-mark" aria-hidden="true">i</span><div><h2 id="scope-title">Small on purpose. Growing with care.</h2><p>Today's release supports plain messages, live reload and recovery. Events and richer gameplay are on the roadmap. The MCP prototype is offline and read-only; AI deployment is absent.</p></div><a class="text-link" :href="withBase('/guide/security.html')">Read the boundaries <span aria-hidden="true">↗</span></a></section>

  <section class="closing-section" aria-labelledby="closing-title"><p class="section-kicker">YOUR NEXT IDEA STARTS HERE</p><h2 id="closing-title">Make a little magic.</h2><p>One file. One reload. Something that is yours.</p><a class="aether-button primary" :href="withBase('/guide/getting-started.html')">Let's build something <span aria-hidden="true">↗</span></a></section>
</div>
</template>
