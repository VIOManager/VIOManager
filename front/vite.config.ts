/**
 * VIOManager 前端 · Vite 配置
 * 对应方案：《前端骨架-开发方案》Step 0 工程初始化
 * - @ 指向 src，全站导入统一用别名
 * - 端口 5174（避开本机常见占用；后端对外 8088）
 * - 联调代理：开发期浏览器请求 /api/** 由 dev server 服务端转发到 8088，
 *   绕开浏览器跨域（后端未配 CORS）；生产部署在 Nginx 后同源，无需代理
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
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8088',
        changeOrigin: true
      }
    }
  }
})
