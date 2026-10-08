/**
 * 应用入口
 * 对应方案：《前端骨架-开发方案》Step 0
 * 注意顺序：pinia 先于 router 安装（路由守卫内 useUserStore 依赖活动的 pinia 实例）
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/tokens.css'
import '@/styles/base.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
