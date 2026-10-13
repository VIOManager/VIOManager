<script setup lang="ts">
/**
 * 论坛站布局壳：通栏白顶栏 + 内容区 + 简版页脚（V2EX 范式）
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/ 样张；《论坛V2EX风格重设计-优化方案》§3
 * ⚠️ 冲突：图签页脚已按 2026-10-09 lzh 拍板废弃（改居中灰字简版），图纸档案语汇禁止回潮论坛站
 * TODO(联调)：搜索框接帖子搜索接口；"创作新主题"接发帖页（本期占位，方案 §8 不做富文本编辑器）
 */
import { useUserStore } from '@/stores/user'
import { avatarOf } from '@/config/avatars'

const user = useUserStore()
const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174'
const year = new Date().getFullYear()
</script>

<template>
  <div class="page">
    <!-- 通栏白顶栏：全宽白底下缘 1px 灰线，内容列宽 1080 -->
    <header class="topbar">
      <div class="wrap bar-in">
        <router-link to="/" class="logo">VIO<em>论坛</em></router-link>
        <span class="search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><line x1="20.5" y1="20.5" x2="16.2" y2="16.2"/></svg>
          <input aria-label="搜索" placeholder="">
        </span>
        <span class="sp"></span>
        <nav class="topnav">
          <router-link to="/" exact-active-class="on">首页</router-link>
          <router-link to="/feed" active-class="on">技术交流</router-link>
        </nav>
        <button class="btn-post" type="button">创作新主题</button>
        <span class="who"><img :src="avatarOf(user.realName, 26)" alt="">{{ user.realName }}</span>
      </div>
    </header>

    <main class="wrap content">
      <router-view />
    </main>

    <!-- 简版页脚：居中灰字 -->
    <footer class="foot">
      <div>
        <a href="#" @click.prevent>关于</a>
        <a :href="mainSite">主站</a>
      </div>
      VIO Manager · 团队论坛 · 内部站点 · {{ year }} 秋
    </footer>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; }
.wrap { max-width: 1080px; margin: 0 auto; padding: 0 10px; }
.bar-in { display: flex; align-items: center; gap: 22px; height: 58px; }
.logo { font-size: 22px; font-weight: 800; color: #222; letter-spacing: -0.02em; }
.logo em { font-style: normal; color: var(--fade); font-weight: 600; font-size: 15px; margin-left: 2px; }
.search { display: flex; align-items: center; width: 230px; height: 31px; background: var(--box); border: 1px solid #D4D4D4; border-radius: 999px; padding: 0 13px; }
.search:focus-within { border-color: #AAA; }
.search svg { width: 14px; height: 14px; color: var(--fade); flex: none; }
.search input { flex: 1; min-width: 0; border: none; background: transparent; font-size: 12.5px; margin-left: 7px; color: var(--body); }
.search input:focus { outline: none; }
.sp { flex: 1; }
.topnav a { font-size: 13px; color: var(--body); margin-left: 18px; }
.topnav a:hover, .topnav a.on { color: #000; }
.btn-post { font-size: 13px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 5px 14px; margin-left: 18px; cursor: pointer; }
.btn-post:hover { border-color: #999; }
.who { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--body); margin-left: 18px; }
.who img { width: 26px; height: 26px; border-radius: var(--radius-ava); }

.content { flex: 1; padding-top: 18px; padding-bottom: 20px; }

.foot { text-align: center; font-size: 12px; color: var(--fade); padding: 22px 0 30px; }
.foot a { color: var(--fade); margin: 0 8px; }
.foot a:hover { color: var(--ink); }
.foot div { margin-bottom: 4px; }
</style>
