<script setup lang="ts">
/**
 * 个人中心回复行列表（MePage 复用组件）
 * 行形态：所属帖标题链接 + #楼层徽 + 楼层内容 + mono 时间与获赞
 * 复用处：我的回复 / 我的点赞·楼层
 */
import type { MyReplyItem } from '@/api/me'

defineProps<{ items: MyReplyItem[] }>()
</script>

<template>
  <div>
    <div v-for="r in items" :key="r.topicId + '-' + r.floorNo" class="rrow">
      <div class="r-top">
        <router-link class="r-topic" :to="`/topic/${r.topicId}`">{{ r.topicTitle }}</router-link>
        <span class="r-floor">#{{ r.floorNo }}</span>
      </div>
      <p class="r-text">{{ r.text }}</p>
      <div class="r-meta">{{ r.publishAt }} · 获赞 {{ r.likeCount }}</div>
    </div>
  </div>
</template>

<style scoped>
.rrow { padding: 12px 16px; border-bottom: 2px solid var(--vio2-row-line); }
.rrow:last-child { border-bottom: none; }
.r-top { display: flex; align-items: center; gap: 10px; }
.r-topic { font-size: 13.5px; font-weight: 800; color: var(--vio2-ink); }
.r-topic:hover { color: var(--vio2-blue); }
.r-floor { font-family: var(--vio2-mono); font-size: 11px; font-weight: 700; color: var(--vio2-ink-3); border: 2px solid var(--vio2-shadow); background: var(--vio2-panel-2); padding: 0 7px; }
.r-text { margin: 7px 0 0; font-size: 13px; line-height: 1.6; color: var(--vio2-ink-2); }
.r-meta { margin-top: 6px; font-family: var(--vio2-mono); font-size: 11.5px; color: var(--vio2-ink-3); }
</style>
