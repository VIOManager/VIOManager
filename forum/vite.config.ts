/**
 * VIOManager 论坛独立站 · Vite 配置
 * 与主站 front/ 同栈（Vite + Vue3 + TS），端口 5175 与主站 5174 并行不冲突
 * - 联调代理：开发期浏览器请求 /api/** 由 dev server 转发到网关 8088，绕开跨域
 *   （论坛服务上线后网关按 /api/forum 与 /api/tech-posts 前缀路由，前端零改动）
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
        target: 'http://localhost:8088',
        changeOrigin: true
      }
    }
  }
})
