/**
 * VIOManager 前端 · Vite 配置
 * 对应方案：《前端骨架-开发方案》Step 0 工程初始化
 * - @ 指向 src，全站导入统一用别名
 * - 端口 5174（避开本机常见占用；后端对外 8088）
 */
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  server: { port: 5174 }
})
