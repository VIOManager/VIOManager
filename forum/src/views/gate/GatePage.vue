<script setup lang="ts">
/**
 * 未登录引导页（论坛不设登录页，JWT 与主站共享）：粗野卡片居中 + 一句引导 + 黄色主按钮
 * 视觉基准：新粗野双主题（2026-10-15 重涂，与主站 front/ 同源 --vio2-* 令牌）
 * 对应：架构方案 §4——论坛服务只验签不查库，登录一律回主站
 * ⚠️ 安全：开发调试按钮仅为骨架/演示阶段放行用（与主站登录桩同性质），联调后必须移除；
 *    正式环境未登录只能回主站
 * 登录通路（开发期两端口不同源，sessionStorage 不互通，方案 §7.1）：
 *   ① 正路：主站登录后侧边栏"VIO 论坛"入口带 ?token= 交接（router 守卫落库）
 *   ② 调试：本页按钮直连管理侧登录换真 JWT（vite 代理 /mgmt-api → 8088）；
 *      真实模式下桩 token 过不了 8089 验签，调试必须走真登录
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { DEV_ACCOUNTS, devLogin, type DevAccount } from '@/api/devLogin'

const router = useRouter()
const user = useUserStore()
const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174/login'

/** 调试按钮仅开发构建可见（生产渲染层不存在） */
const isDev = import.meta.env.DEV
const useMock = import.meta.env.VITE_USE_MOCK === 'true'
const entering = ref<DevAccount | null>(null)

/** 调试进入：mock 模式写桩即可；真实模式走管理侧登录换真 token */
async function devEnter(acc: DevAccount) {
  if (useMock) {
    user.devStubLogin(acc.name, acc.role)
    router.push('/')
    return
  }
  entering.value = acc
  try {
    const token = await devLogin(acc.loginName, acc.password)
    if (token) {
      user.acceptToken(token)
      router.push('/')
    } else {
      ElMessage.error('演示账号登录失败，请确认管理服务 8088 已启动')
    }
  } catch {
    ElMessage.error('连不上管理服务（8088），请确认后端已启动')
  } finally {
    entering.value = null
  }
}
</script>

<template>
  <div class="gate">
    <div class="card">
      <!-- 黑条卡头：与全站 .box-h 同一语言 -->
      <div class="card-h"><b>VIO FORUM</b><span>LOGIN REQUIRED</span></div>
      <div class="in">
        <h1>团队论坛</h1>
        <p class="desc">论坛与主站共用一套账号。<br>请先回主站完成登录，再回到本站。</p>
        <a class="btn-main" :href="mainSite">回主站登录 →</a>
        <div v-if="isDev" class="dev-box">
          <button
            v-for="a in DEV_ACCOUNTS" :key="a.role" class="btn-dev" type="button"
            :disabled="entering !== null" @click="devEnter(a)"
          >调试进入 · {{ a.name }}{{ entering === a ? '…' : '' }}</button>
        </div>
        <p class="annot">※ 登录态有效期 2 小时，过期后回到本页重新引导；调试按钮仅开发构建可见，走演示账号真实登录（生产渲染层直接不存在）。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 独立路由（不套 ForumLayout）：居中卡片自带底纹即 body::before，无需另铺 */
.gate { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; }

.card { width: 430px; background: var(--vio2-panel); border: var(--vio2-bd-main); box-shadow: var(--vio2-hard); }
.card-h { display: flex; align-items: center; justify-content: space-between; background: var(--vio2-head); padding: 10px 16px; border-bottom: var(--vio2-bd-main); }
.card-h b { color: var(--vio2-ink); font-size: 14px; font-weight: 900; letter-spacing: 0.06em; }
.card-h span { font-family: var(--vio2-mono); font-size: 10px; color: #C9C4B4; letter-spacing: 0.14em; }

.in { padding: 38px 36px 26px; text-align: center; }
.in h1 { font-size: 30px; font-weight: 900; color: var(--vio2-ink); letter-spacing: -0.02em; }
.desc { margin: 14px 0 24px; font-size: 13.5px; color: var(--vio2-ink-2); font-weight: 600; line-height: 1.9; }

.btn-main { display: inline-block; background: var(--vio2-yellow); color: var(--vio2-on-ac); border: var(--vio2-bd-acc); font-size: 14px; font-weight: 900; padding: 10px 28px; text-decoration: none; box-shadow: var(--vio2-hard-s); }
.btn-main:hover { transform: translate(2px, 2px); box-shadow: var(--vio2-hard-xs); }

.dev-box { margin-top: 16px; display: grid; gap: 8px; }
.btn-dev { margin: 0 auto; padding: 6px 16px; background: none; border: 2px dashed var(--vio2-bd); color: var(--vio2-ink-3); font-size: 12px; cursor: pointer; }
.btn-dev:hover:not(:disabled) { color: var(--vio2-ink-2); }
.btn-dev:disabled { opacity: 0.45; cursor: wait; }

.annot { margin-top: 22px; font-size: 12px; color: var(--vio2-ink-3); line-height: 1.8; }
</style>
