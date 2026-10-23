<script setup lang="ts">
/**
 * 未登录引导页（论坛不设登录页，JWT 与主站共享）：粗野卡片居中 + 一句引导 + 黄色主按钮
 * 视觉基准：新粗野双主题（2026-10-15 重涂，与主站 front/ 同源 --vio2-* 令牌）
 * 对应：架构方案 §4——论坛服务只验签不查库，登录一律回主站
 * 登录通路（开发期两端口不同源，sessionStorage 不互通，方案 §7.1）：
 *   主站登录后侧边栏"VIO 论坛"入口带 ?token= 交接（router 守卫落库后去掉 query）
 */
const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174/login'
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
        <p class="annot">※ 登录态有效期 2 小时，过期后回到本页重新引导。</p>
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

.annot { margin-top: 22px; font-size: 12px; color: var(--vio2-ink-3); line-height: 1.8; }
</style>
