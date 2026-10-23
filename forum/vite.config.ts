/**
 * VIOManager 论坛独立站 · Vite 配置
 * 与主站 front/ 同栈（Vite + Vue3 + TS），端口 5175 与主站 5174 并行不冲突
 * - 联调代理：开发期浏览器请求 /api/** 由 dev server 转发到论坛服务 8089，绕开跨域
 *   （无网关：vio-forum 独立部署直连；生产同域 Nginx 按最长前缀分流 /api/forum/** → 8089，
 *    前端零改动——技术交流域已并入论坛，/api/tech-posts 前缀随之后端退役）
 * - 生产 base（方案 §7.2）：单域名同源路径分区，论坛静态资源挂在 /forum/ 下
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  base: process.env.NODE_ENV === 'production' ? '/forum/' : '/',
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: {
    port: 5175,
    proxy: {
      '/api': {
        target: 'http://localhost:8089',
        changeOrigin: true
      }
    }
  }
})
