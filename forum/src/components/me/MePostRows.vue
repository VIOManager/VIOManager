<script setup lang="ts">
/**
 * 个人中心帖子行列表（MePage 复用组件）
 * 行骨架全走 skin.css 的 .cell（与首页主题行同一语言）：方头像 + 标题 + mono meta + 回复数块
 * 复用处：我的主题 / 我的收藏·论坛帖 / 我的点赞·帖
 */
import { useUserStore } from '@/stores/user'
import { boardName } from '@/config/boards'
import type { TopicItem } from '@/api/forum'

/** 头像统一走 store：本人已换自定义头像时，我的主题/收藏/点赞列表里也跟着变 */
const user = useUserStore()

defineProps<{ items: TopicItem[] }>()
</script>

<template>
  <div>
    <div v-for="t in items" :key="t.id" class="cell">
      <img class="ava" :src="user.avatarByName(t.author, 48)" :alt="t.author">
      <div class="mid">
        <div class="t-title">
          <span v-if="t.pinned" class="pin">置顶</span>
          <router-link :to="`/topic/${t.id}`">{{ t.title }}</router-link>
        </div>
        <div class="t-meta">
          <span class="node">{{ boardName(t.board) }}</span><b>{{ t.author }}</b> · {{ t.lastActiveAt }} · 最后回复 <b>{{ t.lastReplyBy }}</b>
        </div>
      </div>
      <span class="pill" :class="{ hot: t.hot }">{{ t.replyCount }}</span>
    </div>
  </div>
</template>

<style scoped>
/* .mid 在首页是页级 scoped，这里自带一份保证弹性占位 */
.mid { flex: 1; min-width: 0; }
</style>
