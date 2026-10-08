<script setup lang="ts">
/**
 * 未登录引导页（论坛不设登录页，JWT 与主站共享）
 * 对应：架构方案 §4——论坛服务只验签不查库，登录一律回主站
 */
const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174/login'
</script>

<template>
  <div class="gate">
    <div class="gate-card">
      <h1>团队论坛</h1>
      <p class="en mono">REVIEW BOARD · VIOMANAGER</p>
      <p class="desc">论坛与主站共用一套账号。请先回主站完成登录登记，再回到本站。</p>
      <a class="btn-ink" :href="mainSite">回主站登录</a>
      <p class="annot">※ 登录态有效期 2 小时，过期后回到本页重新引导。</p>
    </div>
  </div>
</template>

<style scoped>
.gate { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--vio-paper); }
.gate-card { width: 420px; background: var(--vio-sheet); border: 1px solid var(--vio-ink); box-shadow: 5px 5px 0 rgba(29, 58, 143, 0.14); padding: 40px 36px 30px; text-align: center; }
.gate-card h1 { font-family: var(--vio-serif); font-size: 30px; letter-spacing: 0.24em; font-weight: 700; }
.en { margin-top: 6px; font-size: 10.5px; letter-spacing: 0.26em; color: var(--vio-blue-ink); }
.desc { margin: 22px 0 26px; font-size: 13.5px; color: var(--vio-ink-2); line-height: 2; }
.btn-ink { display: inline-block; padding: 11px 34px; background: var(--vio-blue-ink); color: #F6F7F2; font-size: 14px; letter-spacing: 0.24em; text-indent: 0.24em; font-family: var(--vio-serif); font-weight: 700; transition: background 0.3s var(--vio-ease); }
.btn-ink:hover { background: var(--vio-blue-deep); }
.annot { margin-top: 20px; font-family: var(--vio-fangsong); font-size: 11.5px; color: var(--vio-ink-3); }
</style>
