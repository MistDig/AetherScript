<script setup>
import { ref } from 'vue'
import { useData, withBase } from 'vitepress'
const { theme } = useData()
const status = ref('')
async function copyDocs() {
  try {
    const path = '/releases/' + theme.value.releaseVersion + '/llms-full.txt'
    await navigator.clipboard.writeText(new URL(withBase(path), window.location.origin).href)
    status.value = 'Copied'
    window.setTimeout(() => { status.value = '' }, 2500)
  } catch {
    status.value = 'Use Download docs to attach the guide to your AI.'
  }
}
</script>

<template>
  <div class="ai-docs-links">
    <button type="button" @click="copyDocs">Copy docs link for AI</button>
    <a :href="withBase('/releases/' + theme.releaseVersion + '/llms-full.txt')" download>Download docs</a>
    <span role="status">{{ status }}</span>
  </div>
</template>

<style scoped>
.ai-docs-links { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; font-size: .85rem; padding: .6rem; }
button { cursor: pointer; border: 1px solid var(--vp-c-divider); padding: .35rem .65rem; border-radius: .4rem; color: var(--vp-c-brand-1); }
a { color: var(--vp-c-brand-1); text-decoration: underline; }
@media (max-width: 960px) {
  .ai-docs-links { gap: .4rem; font-size: .75rem; }
  .ai-docs-links a { display: none; }
}
</style>
