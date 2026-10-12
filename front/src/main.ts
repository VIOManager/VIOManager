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
import '@/styles/page.css'
import App from './App.vue'
import router from './router'
// axios 实例与 mock 层装配：拦截器随模块加载生效（页面只引 @/api/{模块} 的请求函数，不直接碰本模块）
import '@/api/http'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
