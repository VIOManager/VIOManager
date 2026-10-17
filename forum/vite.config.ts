/**
 * VIOManager 论坛独立站 · Vite 配置
 * 与主站 front/ 同栈（Vite + Vue3 + TS），端口 5175 与主站 5174 并行不冲突
 * - 联调代理：开发期浏览器请求 /api/** 由 dev server 转发到论坛服务 8089，绕开跨域
 *   （无网关：vio-forum 独立部署直连；生产同域 Nginx 按最长前缀分流 /api/forum/** → 8089，
 *    前端零改动——技术交流域已并入论坛，/api/tech-posts 前缀随之后端退役）
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://localhost:8089',
        changeOrigin: true
      },
      // 调试登录专用：管理侧登录接口在 8088，浏览器直连跨源会被 CORS 拦（生产同域 Nginx，无此代理）
      '/mgmt-api': {
        target: 'http://localhost:8088',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/mgmt-api/, '/api')
      }
    }
  }
})
