<script setup lang="ts">
/**
 * 论坛站布局壳：黑条顶栏 + 内容区 + mono 页脚（新粗野双主题，2026-10-16 重涂）
 * 视觉基准：.agentdocs/design-preview/2026-10-15-forum-redesign/ 样张（lzh 已拍板）
 * ⚠️ 冲突：旧 V2EX 皮肤（白顶栏/圆角/柔阴影）整体退场；图纸档案语汇仍禁止回潮论坛站
 * 搜索：回车跳首页带 q 关键词（F01 keyword 参数），首页读取 route.query.q
 * 主题：顶栏 ☾/☀ 钮切 图纸白/深空黑（stores/theme.ts，偏好存 localStorage vio_theme）
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { avatarOf } from '@/config/avatars'
import PostDialog from '@/components/PostDialog.vue'

const user = useUserStore()
const theme = useThemeStore()
const router = useRouter()
const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174'
const year = new Date().getFullYear()

const searchWord = ref('')
const postVisible = ref(false)

/** 搜索回车：带 q 跳首页（首页 watch route.query.q 触发查询） */
function doSearch() {
  const q = searchWord.value.trim()
  router.push({ path: '/', query: q ? { q } : {} })
}

/** 发帖成功后回首页并刷新列表（新帖在最新排序的顶部） */
function onPosted() {
  router.push({ path: '/', query: { t: Date.now() } })
}
</script>

<template>
  <div class="page">
    <div class="orb"></div>

    <!-- 恒黑顶栏：黄块 logo + mono 面包屑 + 搜索 + 导航 + 发帖黄钮 + 主题钮 + 方头像 -->
    <header class="bar">
      <div class="bar-in">
        <router-link to="/" class="lg">VIO</router-link>
        <span class="lg2">论坛</span>
        <span class="crumb">FORUM / 团队技术集散地</span>
        <span class="search">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><line x1="20.5" y1="20.5" x2="16.2" y2="16.2"/></svg>
          <input v-model="searchWord" aria-label="搜索" placeholder="搜索主题与标签" @keyup.enter="doSearch">
        </span>
        <span class="sp"></span>
        <nav class="topnav">
          <router-link to="/" exact-active-class="on">首页</router-link>
        </nav>
        <button class="btn-bar" type="button" @click="postVisible = true">＋ 创作新主题</button>
        <button class="tg" type="button" :aria-label="theme.current === 'light' ? '切换到深色' : '切换到浅色'" @click="theme.toggle()">
          {{ theme.current === 'light' ? '☾' : '☀' }}
        </button>
        <img class="av" :src="avatarOf(user.realName, 34)" :alt="user.realName" :title="user.realName">
      </div>
    </header>

    <main class="wrap">
      <router-view />
    </main>

    <!-- mono 页脚 -->
    <footer class="foot">
      <div>
        <a href="#" @click.prevent>关于</a>
        <a :href="mainSite">主站</a>
      </div>
      VIO MANAGER · 团队论坛 · 内部站点 · {{ year }} 秋
    </footer>

    <!-- 全站发帖入口（成功后回首页看新帖） -->
    <PostDialog v-model="postVisible" @created="onPosted" />
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; position: relative; z-index: 1; }
.wrap { max-width: 1080px; margin: 0 auto; padding: 26px 10px 0; width: 100%; }

/* 顶栏：恒黑条（深色态加下缘线），内容列宽 1080 */
.bar { position: sticky; top: 0; z-index: 10; background: var(--vio2-head); color: #FFF; }
:root[data-theme='dark'] .bar { border-bottom: var(--vio2-bd-main); }
.bar-in { max-width: 1080px; margin: 0 auto; display: flex; align-items: center; gap: 16px; height: 60px; padding: 0 10px; }
.lg { background: var(--vio2-yellow); color: #101010; font-weight: 900; font-size: 16px; padding: 4px 11px; }
.lg:hover { color: #101010; }
.lg2 { font-weight: 900; font-size: 15px; letter-spacing: 0.06em; color: #FFF; }
.crumb { font-family: var(--vio2-mono); font-size: 11px; letter-spacing: 0.14em; color: #8F8A7A; border-left: 3px solid #4A463A; padding-left: 16px; }
:root[data-theme='dark'] .crumb { color: var(--vio2-ink-3); border-left-color: var(--vio2-bd); }

.search { display: flex; align-items: center; gap: 7px; width: 230px; padding: 7px 13px; border: 3px solid #000; background: #FFF; color: #7A7562; font-size: 12.5px; box-shadow: 3px 3px 0 rgba(255, 255, 255, 0.25); }
:root[data-theme='dark'] .search { background: var(--vio2-panel); border-color: var(--vio2-bd); color: var(--vio2-ink-3); box-shadow: 3px 3px 0 #04060B; }
.search svg { width: 14px; height: 14px; flex: none; }
.search input { flex: 1; min-width: 0; border: none; background: transparent; font-size: 12.5px; color: inherit; font-family: var(--vio2-din); }
.search input:focus { outline: none; }
:root[data-theme='dark'] .search input { color: var(--vio2-ink); }

.sp { flex: 1; }
.topnav a { color: #C9C4B4; font-size: 13px; font-weight: 800; margin-left: 18px; }
.topnav a:hover { color: #FFF; }
.topnav a.on { color: var(--vio2-yellow); }

/* 发帖黄钮：白/黑投影随主题，按压下沉 */
.btn-bar { background: var(--vio2-yellow); color: #101010; border: 3px solid #000; box-shadow: 3px 3px 0 rgba(255, 255, 255, 0.25); font: inherit; font-size: 13px; font-weight: 900; padding: 8px 16px; cursor: pointer; letter-spacing: 0.06em; margin-left: 4px; transition: transform 0.12s, box-shadow 0.12s; }
.btn-bar:hover { transform: translate(1px, 1px); box-shadow: 2px 2px 0 rgba(255, 255, 255, 0.25); }
.btn-bar:active { transform: translate(3px, 3px); box-shadow: none; }
:root[data-theme='dark'] .btn-bar { border-color: #04060B; box-shadow: 3px 3px 0 #04060B; }
:root[data-theme='dark'] .btn-bar:hover { box-shadow: 2px 2px 0 #04060B; }

/* 主题切换钮：浅色白块 / 深色面板块黄字 */
.tg { width: 40px; height: 36px; border: 3px solid #000; background: #FFF; color: #101010; font-size: 15px; cursor: pointer; box-shadow: 3px 3px 0 rgba(255, 255, 255, 0.25); }
:root[data-theme='dark'] .tg { background: var(--vio2-panel); border-color: var(--vio2-bd); color: var(--vio2-yellow); box-shadow: 3px 3px 0 #04060B; }

/* 头像：方形粗边（brutalist 不做圆角） */
.av { width: 34px; height: 34px; border: 3px solid #000; object-fit: cover; }
:root[data-theme='dark'] .av { border-color: #04060B; }

main.wrap { flex: 1; padding-bottom: 20px; }

@media (max-width: 900px) {
  .search { display: none; }
  .crumb { display: none; }
}
</style>
