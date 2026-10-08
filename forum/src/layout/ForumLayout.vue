<script setup lang="ts">
/**
 * 论坛站布局壳：顶栏 + 内容区 + 页脚图签
 * 视觉：图纸档案家族（期刊/会审语汇）；设计稿 design-preview/forum-site/
 * TODO(联调)：发帖按钮接发帖页（暂不做富文本，纯文本+附件走主站文件服务）
 */
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const year = new Date().getFullYear()
</script>

<template>
  <div class="page">
    <header class="topbar">
      <router-link to="/" class="brand">
        <b>VIO</b><span>团队论坛</span><i>REVIEW BOARD · 独立站</i>
      </router-link>
      <nav class="topnav">
        <router-link to="/" exact-active-class="active">首页</router-link>
        <router-link to="/feed" active-class="active">技术交流</router-link>
        <router-link to="/kb" active-class="active">知识库</router-link>
      </nav>
      <div class="top-right">
        <input type="text" placeholder="搜索帖子 / 标签" />
        <button class="btn-ink" type="button">发帖</button>
        <span class="who"><i class="ava">{{ user.initials }}</i>{{ user.realName }}</span>
      </div>
    </header>

    <main class="content">
      <router-view />
    </main>

    <footer class="titleblock">
      <div><i>站点</i><b>VIOManager 团队论坛（独立站）</b></div>
      <div><i>图号</i><b class="mono">VIO-FORUM</b></div>
      <div><i>学期</i><b class="mono">{{ year }} 秋</b></div>
      <div><i>版次</i><b class="mono">A</b></div>
    </footer>
  </div>
</template>

<style scoped>
.page { max-width: 1240px; margin: 0 auto; background: var(--vio-paper); box-shadow: 0 10px 34px rgba(27, 35, 48, 0.14); min-height: 100vh; display: flex; flex-direction: column; }
.topbar { display: flex; align-items: center; gap: 28px; padding: 16px 32px; background: var(--vio-sheet); border-bottom: 2px solid var(--vio-ink); }
.brand { display: flex; align-items: baseline; gap: 10px; white-space: nowrap; }
.brand b { font-family: var(--vio-disp-latin); font-size: 15px; letter-spacing: 0.06em; border: 1.5px solid var(--vio-ink); padding: 3px 8px; color: var(--vio-ink); }
.brand span { font-family: var(--vio-serif); font-size: 17px; font-weight: 700; letter-spacing: 0.14em; color: var(--vio-ink); }
.brand i { font-style: normal; font-family: var(--vio-mono); font-size: 9.5px; color: var(--vio-ink-3); letter-spacing: 0.18em; }
.topnav { display: flex; gap: 22px; flex: 1; }
.topnav a { font-size: 13.5px; color: var(--vio-ink-2); letter-spacing: 0.1em; padding-bottom: 3px; border-bottom: 2px solid transparent; }
.topnav a.active { color: var(--vio-blue-ink); font-weight: 700; border-bottom-color: var(--vio-blue-ink); }
.top-right { display: flex; align-items: center; gap: 14px; }
.top-right input { width: 180px; border: none; border-bottom: 1px solid var(--vio-ink-3); background: transparent; padding: 6px 2px; font-size: 13px; color: var(--vio-ink); border-radius: 0; }
.top-right input:focus { outline: none; border-bottom: 2px solid var(--vio-blue-ink); }
.btn-ink { padding: 8px 22px; background: var(--vio-ink); color: var(--vio-paper); border: none; font-size: 13px; letter-spacing: 0.18em; text-indent: 0.18em; font-family: var(--vio-serif); font-weight: 700; border-radius: 0; transition: background 0.25s var(--vio-ease); }
.btn-ink:hover { background: var(--vio-blue-ink); }
.who { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--vio-ink-2); }
.ava { display: inline-grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: var(--vio-wash); color: var(--vio-blue-ink); font-size: 11px; font-weight: 700; font-style: normal; font-family: var(--vio-serif); border: 1px solid var(--vio-rule); }
.content { flex: 1; padding: 26px 32px 40px; }
.titleblock { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; border: 2px solid var(--vio-ink); background: var(--vio-sheet); margin: 0 32px 30px; }
.titleblock div { border-right: 1px solid var(--vio-ink); padding: 6px 12px; }
.titleblock div:last-child { border-right: none; }
.titleblock i { display: block; font-style: normal; font-size: 9.5px; letter-spacing: 0.16em; color: var(--vio-ink-3); font-family: var(--vio-fangsong); }
.titleblock b { font-size: 12px; }
</style>
