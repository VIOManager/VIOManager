<script setup lang="ts">
/**
 * 未登录引导页（论坛不设登录页，JWT 与主站共享）：白盒居中 + 一句引导 + 白底描边按钮
 * 依据：《论坛V2EX风格重设计-优化方案》§5.5（废图签装饰与蓝图网格背景）
 * 对应：架构方案 §4——论坛服务只验签不查库，登录一律回主站
 * ⚠️ 安全：开发调试按钮仅为骨架/演示阶段放行用（与主站登录桩同性质），
 *    联调后必须移除；正式环境未登录只能回主站
 * 注意：开发期主站 5174 与论坛 5175 不同源，localStorage 不互通，需要本按钮补桩；
 *    生产环境同挂 8088 网关下同源，主站登录后论坛直接可用
 */
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const user = useUserStore()
const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174/login'

/** 开发调试：写入 stub token（同步 store，否则路由守卫读不到）后进入首页 */
function devEnter() {
  user.devStubLogin()
  router.push('/')
}
</script>

<template>
  <div class="gate">
    <div class="box card">
      <h1>VIO 论坛</h1>
      <p class="desc">论坛与主站共用一套账号。请先回主站完成登录，再回到本站。</p>
      <a class="btn-post" :href="mainSite">回主站登录</a>
      <button class="btn-dev" type="button" @click="devEnter">调试进入（开发环境）</button>
      <p class="annot">※ 登录态有效期 2 小时，过期后回到本页重新引导；调试按钮仅开发环境可见，联调后移除。</p>
    </div>
  </div>
</template>

<style scoped>
.gate { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg); }
.card { width: 400px; background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); padding: 44px 38px 30px; text-align: center; }
.card h1 { font-size: 22px; font-weight: 800; color: #222; letter-spacing: -0.02em; }
.desc { margin: 16px 0 22px; font-size: 13.5px; color: var(--body); line-height: 1.8; }
.btn-post { display: inline-block; font-size: 13px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 7px 22px; }
.btn-post:hover { border-color: #999; }
.btn-dev { display: block; margin: 14px auto 0; padding: 6px 16px; background: none; border: 1px dashed #CCC; color: var(--fade); font-size: 12px; border-radius: var(--radius-box); cursor: pointer; }
.btn-dev:hover { border-color: var(--fade); color: var(--body); }
.annot { margin-top: 20px; font-size: 12px; color: var(--fade); line-height: 1.8; }
</style>
