<script setup lang="ts">
/**
 * 未登录引导页（论坛不设登录页，JWT 与主站共享）
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
    <div class="gate-card">
      <p class="en mono">VIOMANAGER · REVIEW BOARD</p>
      <h1>团队论坛</h1>
      <p class="desc">论坛与主站共用一套账号。请先回主站完成登录登记，再回到本站。</p>
      <a class="btn-ink" :href="mainSite">回主站登录</a>
      <button class="btn-dev" type="button" @click="devEnter">调试进入（开发环境）</button>
      <p class="annot">※ 登录态有效期 2 小时，过期后回到本页重新引导；调试按钮仅开发环境可见，联调后移除。</p>
    </div>
    <footer class="titleblock" aria-hidden="true">
      <div><i>站点</i><b>VIOManager 团队论坛（独立站）</b></div>
      <div><i>图号</i><b class="mono">VIO-FORUM-GATE</b></div>
      <div><i>说明</i><b>未登录引导</b></div>
      <div><i>版次</i><b class="mono">A</b></div>
    </footer>
  </div>
</template>

<style scoped>
.gate {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 26px;
  background:
    linear-gradient(90deg, rgba(29, 58, 143, 0.05) 1px, transparent 1px),
    linear-gradient(180deg, rgba(29, 58, 143, 0.05) 1px, transparent 1px),
    var(--vio-paper);
  background-size: 24px 24px, 24px 24px;
}
.gate-card {
  width: 440px;
  background: var(--vio-sheet);
  border: 1px solid var(--vio-ink);
  box-shadow: 5px 5px 0 rgba(29, 58, 143, 0.14);
  padding: 40px 38px 28px;
  text-align: center;
}
.en { font-size: 10.5px; letter-spacing: 0.28em; color: var(--vio-blue-ink); }
.gate-card h1 { margin-top: 8px; font-family: var(--vio-serif); font-size: 32px; letter-spacing: 0.24em; font-weight: 700; }
.desc { margin: 20px 0 24px; font-size: 13.5px; color: var(--vio-ink-2); line-height: 2; }
.btn-ink {
  display: inline-block;
  padding: 11px 34px;
  background: var(--vio-blue-ink);
  color: #F6F7F2;
  font-size: 14px;
  letter-spacing: 0.24em;
  text-indent: 0.24em;
  font-family: var(--vio-serif);
  font-weight: 700;
  transition: background 0.3s var(--vio-ease);
}
.btn-ink:hover { background: var(--vio-blue-deep); }
.btn-dev {
  display: block;
  margin: 12px auto 0;
  padding: 7px 18px;
  background: none;
  border: 1px dashed var(--vio-ink-3);
  color: var(--vio-ink-3);
  font-size: 12px;
  letter-spacing: 0.08em;
  border-radius: 0;
  transition: all 0.25s var(--vio-ease);
}
.btn-dev:hover { border-color: var(--vio-blue-ink); color: var(--vio-blue-ink); }
.annot { margin-top: 20px; font-family: var(--vio-fangsong); font-size: 11.5px; color: var(--vio-ink-3); line-height: 1.9; }
.titleblock { width: 440px; display: grid; grid-template-columns: 1.8fr 1fr 1fr 0.6fr; border: 2px solid var(--vio-ink); background: var(--vio-sheet); }
.titleblock div { border-right: 1px solid var(--vio-ink); padding: 6px 12px; }
.titleblock div:last-child { border-right: none; }
.titleblock i { display: block; font-style: normal; font-size: 9.5px; letter-spacing: 0.16em; color: var(--vio-ink-3); font-family: var(--vio-fangsong); }
.titleblock b { font-size: 11.5px; font-weight: 600; }
</style>
