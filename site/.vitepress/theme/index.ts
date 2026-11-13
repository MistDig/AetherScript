import { h } from 'vue'
import DefaultTheme from 'vitepress/theme'
import AetherHome from './AetherHome.vue'
import AiDocsButton from './AiDocsButton.vue'
import './custom.css'
export default {
  extends: DefaultTheme,
  Layout: () => h(DefaultTheme.Layout, null, { 'nav-bar-content-after': () => h(AiDocsButton) }),
  enhanceApp({ app }) {
    app.component('AetherHome', AetherHome)
    app.component('AiDocsButton', AiDocsButton)
  }
}
