<script setup lang="ts">
/**
 * AI 悬浮球（全站右下角固定入口，双入口之二——菜单进整页之外的任意页唤起）
 * 设计样张：.agentdocs/design-preview/2026-10-23-ai-assistant/
 * 在 /ai 页自身隐藏（已在场，悬着碍事）
 * TODO(联调)：AI02 enabled=false 时灰化（需全局缓存探针结果，避免每页多打一次接口）
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const hidden = computed(() => route.path === '/ai')
</script>

<template>
  <router-link v-if="!hidden" to="/ai" class="fab" title="AI 助手">
    <span class="fab-tip mono">任意页面可唤起 <b>AI 助手</b></span>
    ✦ AI
  </router-link>
</template>

<style scoped>
.fab {
  position: fixed; right: 34px; bottom: 34px; z-index: 90;
  width: 62px; height: 62px; border-radius: 0; /* 粗野风不做圆 */
  background: var(--vio2-yellow); color: #101010; border: 3px solid var(--vio2-bd);
  box-shadow: 5px 5px 0 var(--vio2-shadow); font-weight: 900; font-size: 17px; cursor: pointer;
  display: grid; place-items: center; letter-spacing: 0.02em; text-decoration: none;
}
.fab:hover { transform: translate(1px, 1px); box-shadow: 4px 4px 0 var(--vio2-shadow); }
.fab-tip {
  position: absolute; right: 76px; bottom: 18px; white-space: nowrap;
  background: var(--vio2-head); color: #fff; border: 2px solid var(--vio2-bd);
  box-shadow: 3px 3px 0 var(--vio2-shadow); font-size: 11.5px; font-weight: 700; padding: 5px 11px;
  opacity: 0; pointer-events: none; transition: opacity 0.15s var(--vio-ease);
}
.fab-tip b { color: var(--vio2-yellow); }
.fab:hover .fab-tip { opacity: 1; }
@media (max-width: 840px) {
  .fab { right: 16px; bottom: 16px; width: 52px; height: 52px; font-size: 15px; }
  .fab-tip { display: none; }
}
</style>
