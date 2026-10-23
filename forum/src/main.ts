/**
 * 论坛站入口
 * 视觉：新粗野双主题（tokens.css 令牌 + skin.css 共享组件，与主站同一设计系统）
 */
// token 接力必须先于一切模块（含 router 的 createWebHistory）执行，见该文件头注释
import './bootstrap-token'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/tokens.css'
import '@/styles/base.css'
import '@/styles/skin.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
