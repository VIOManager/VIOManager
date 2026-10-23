<script setup lang="ts">
/**
 * 楼层列表（TopicPage 拆分件）：一级分页楼层 + V4 二级评论（children 缩进、无楼层号、
 * 操作行无"回复"——两级封顶 9106 前端先拦）+ 楼层点赞（按楼层粒度操作锁）。
 * 管理入口（拍板：操作行直接追加）：isAdmin 时每层追加"管理"，弹 AdminDialog。
 * HIDDEN 楼层文本由服务端拼好（学生占位/管理员原文），前端只加管理员侧的"已隐藏"标识。
 */
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'
import { apiLikeReply, type ReplyItem } from '@/api/forum'

const props = defineProps<{
  floors: ReplyItem[]
  /** 管理档两判定（超管+部门管理员）：管理按钮显隐 */
  isAdmin: boolean
}>()
const emit = defineEmits<{
  (e: 'replyTo', floor: ReplyItem): void
  (e: 'quote', floor: ReplyItem): void
  (e: 'admin', floor: ReplyItem): void
}>()

/** 头像统一走 store（本人已换自定义头像时楼层里也是新的） */
const user = useUserStore()

/** 楼层点赞操作锁：天然幂等操作，锁只为防连点白发一堆请求（同主楼口径） */
const likeLocks = ref<Record<string, boolean>>({})
async function toggleLike(f: ReplyItem) {
  if (likeLocks.value[f.id]) return
  likeLocks.value[f.id] = true
  try {
    const target = !f.liked
    const res = await apiLikeReply(f.id, target)
    f.liked = res.liked
    f.likeCount += res.liked ? 1 : -1
  } finally {
    likeLocks.value[f.id] = false
  }
}

/** edited 标记：隐藏楼层学生只见占位文本，"已编辑"元数据一并隐去（管理员看原文，标记仍有意义） */
function showEdited(r: ReplyItem): boolean {
  return !!r.edited && (props.isAdmin || r.status !== 'HIDDEN')
}
</script>

<template>
  <div
    v-for="f in floors"
    :key="f.id"
    class="floor"
    :class="{ 'is-hidden': isAdmin && f.status === 'HIDDEN' }"
  >
    <img class="ava" :src="user.avatarByName(f.author, 48)" :alt="f.author">
    <div class="f-main">
      <div class="f-meta"><b>{{ f.author }}</b>{{ f.authorGroup }} · {{ f.publishAt }}<span class="fno">#{{ f.floorNo }}</span></div>
      <div v-if="f.quote" class="quote">@{{ f.quote.author }} #{{ f.quote.floorNo }}：{{ f.quote.text }}</div>
      <div class="f-text">{{ f.text }}<span v-if="showEdited(f)" class="mark">已编辑</span><span v-if="isAdmin && f.status === 'HIDDEN'" class="mark hid">已隐藏</span></div>
      <div class="f-ops">
        <a href="#" :class="{ on: f.liked }" @click.prevent="toggleLike(f)">♥ {{ f.likeCount }}</a>
        <a href="#reply" @click.prevent="emit('replyTo', f)">回复</a>
        <a href="#reply" @click.prevent="emit('quote', f)">引用</a>
        <a v-if="isAdmin" href="#" class="adm" @click.prevent="emit('admin', f)">管理</a>
      </div>
      <!-- 二级评论：缩进、不展示楼层号（P2 口径：二级占号但不进楼层序列展示） -->
      <div v-for="c in f.children" :key="c.id" class="child">
        <img class="cava" :src="user.avatarByName(c.author, 32)" :alt="c.author">
        <div class="c-main">
          <div class="f-meta"><b>{{ c.author }}</b>{{ c.authorGroup }} · {{ c.publishAt }}</div>
          <div v-if="c.quote" class="quote">@{{ c.quote.author }} #{{ c.quote.floorNo }}：{{ c.quote.text }}</div>
          <div class="f-text">{{ c.text }}<span v-if="showEdited(c)" class="mark">已编辑</span><span v-if="isAdmin && c.status === 'HIDDEN'" class="mark hid">已隐藏</span></div>
          <div class="f-ops">
            <a href="#" :class="{ on: c.liked }" @click.prevent="toggleLike(c)">♥ {{ c.likeCount }}</a>
            <a href="#reply" @click.prevent="emit('quote', c)">引用</a>
            <a v-if="isAdmin" href="#" class="adm" @click.prevent="emit('admin', c)">管理</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 楼层骨架/头像/meta/引用/操作行走 skin.css 共享件；这里只放二级缩进与管理标记 */
.child { display: flex; gap: 10px; margin-top: 10px; padding: 8px 10px; border-left: 3px solid var(--vio2-bd-row); background: var(--vio2-panel-2); }
.cava { width: 32px; height: 32px; flex: none; border: var(--vio2-bd-row); object-fit: cover; }
.c-main { flex: 1; min-width: 0; }
.c-main .f-meta { font-size: 11px; }
.c-main .f-text { font-size: 12.5px; }
.c-main .f-ops { margin-top: 4px; }
.mark { display: inline-block; margin-left: 8px; padding: 0 5px; font-size: 10.5px; font-family: var(--vio2-mono); color: var(--vio2-ink-3); border: 1px solid var(--vio2-bd-row); }
.mark.hid { color: var(--vio2-pink); border-color: var(--vio2-pink); }
.f-ops a.adm { color: var(--vio2-lilac); font-weight: 700; }
.floor.is-hidden { opacity: 0.75; }
</style>
