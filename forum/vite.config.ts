/**
 * VIOManager 论坛独立站 · Vite 配置
 * 与主站 front/ 同栈（Vite + Vue3 + TS），端口 5175 与主站 5174 并行不冲突
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: { port: 5175 }
})
