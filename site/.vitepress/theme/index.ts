import DefaultTheme from 'vitepress/theme'
import AetherHome from './AetherHome.vue'
import './custom.css'
export default { extends: DefaultTheme, enhanceApp({ app }) { app.component('AetherHome', AetherHome) } }
