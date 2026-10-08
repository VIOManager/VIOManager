<script setup lang="ts">
/**
 * 管理端布局壳：侧边目录 + 顶栏 + 内容区
 * 对应方案：《前端骨架-开发方案》Step 2（布局与路由守卫）
 * 视觉：方向一「图纸档案」——侧边栏为编号目录式，内容区为纸面
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
  display: grid;
  grid-template-columns: 228px 1fr;
  min-height: 100vh;
  background: var(--vio-paper);
}
.shell-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.shell-content {
  flex: 1;
  padding: 26px 32px 34px;
}
</style>
