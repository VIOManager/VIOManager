<script setup lang="ts">
/**
 * 管理端布局壳：侧边目录 + 顶栏 + 内容区
 * 对应方案：《前端骨架-开发方案》Step 2；S2 双主题粗野重涂（2026-10-15 方案）
 * 壳层用 --vio2-* 新令牌；内容页面迁移期间仍用旧令牌（方案"迁移共存策略"）
 */
import SideMenu from './SideMenu.vue'
import TopBar from './TopBar.vue'
</script>

<template>
  <div class="shell">
    <SideMenu />
    <div class="shell-main">
      <TopBar />
      <main class="shell-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  position: relative;
  display: grid;
  grid-template-columns: 232px 1fr;
  min-height: 100vh;
  background: var(--vio2-bg);
}
/* 深空黑主题的工程网格底纹（图纸白为透明不显示） */
.shell::before {
  content: "";
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(var(--vio2-grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--vio2-grid) 1px, transparent 1px);
  background-size: 56px 56px;
}
.shell-main { min-width: 0; display: flex; flex-direction: column; }
.shell-content { flex: 1; padding: 28px 36px 40px; }
</style>
