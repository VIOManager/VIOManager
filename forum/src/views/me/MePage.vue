<script setup lang="ts">
/**
 * 个人中心（/me）——lzh 2026-10-23 需求："点赞收藏没有能看到的地方、个人没有创作的地方"
 * 结构：资料头卡（方头像 + 身份 + 四格统计）+ 五 tab（主题/回复/收藏/点赞/订阅）
 * 数据：F11–F14（论坛清单未落盘，mock 占位编号）；收藏含技术交流 T03 一路
 * 订阅口径：本期不落库存 localStorage（技术交流清单 §3 拍板），
 *    与 FeedPage 共用 forum_tech_subs 键——这里撤销、技术交流页重新进入即同步
 */
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  apiMyTopics, apiMyReplies, apiMyCollections, apiMyLikes,
  type MyReplyItem, type MyCollections, type MyLikes
} from '@/api/me'
import type { TopicItem } from '@/api/forum'
import MePostRows from '@/components/me/MePostRows.vue'
import MeReplyRows from '@/components/me/MeReplyRows.vue'
import MeAvatarDialog from '@/components/me/MeAvatarDialog.vue'

const user = useUserStore()

/** 换头像弹窗显隐（头像本体即入口） */
const avVisible = ref(false)

/** 与 FeedPage 同源的订阅存储键（那边写入、这边可看可撤） */
const SUBS_KEY = 'forum_tech_subs'

type TabKey = 'topics' | 'replies' | 'collections' | 'likes' | 'subs'
const TABS: { key: TabKey; name: string }[] = [
  { key: 'topics', name: '我的主题' },
  { key: 'replies', name: '我的回复' },
  { key: 'collections', name: '我的收藏' },
  { key: 'likes', name: '我的点赞' },
  { key: 'subs', name: '我的订阅' }
]
const tab = ref<TabKey>('topics')

const myTopics = ref<TopicItem[]>([])
const myReplies = ref<MyReplyItem[]>([])
const myCollections = ref<MyCollections>({ topics: [], techPosts: [] })
const myLikes = ref<MyLikes>({ topics: [], replies: [] })

async function loadAll() {
  const [t, r, c, l] = await Promise.all([apiMyTopics(), apiMyReplies(), apiMyCollections(), apiMyLikes()])
  myTopics.value = t
  myReplies.value = r
  myCollections.value = c
  myLikes.value = l
}

/** 角色码 → 中文（mock 三值，未知码兜底"成员"；联调后由 JWT 载荷解析） */
const ROLE_LABEL: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  DEPT_ADMIN: '部门管理员',
  STUDENT: '学生'
}
const roleLabel = computed(() => ROLE_LABEL[user.role] ?? '成员')

/** 头卡统计：获赞 = 我帖获赞 + 我楼层获赞；收藏 = 论坛帖 + 技术推送 */
const stats = computed(() => {
  let likeTotal = 0
  for (const t of myTopics.value) likeTotal += t.likeCount
  for (const r of myReplies.value) likeTotal += r.likeCount
  return {
    topics: myTopics.value.length,
    replies: myReplies.value.length,
    likes: likeTotal,
    collects: myCollections.value.topics.length + myCollections.value.techPosts.length
  }
})

// ── 我的订阅：读 localStorage（技术交流页同键同源）──
const subs = ref<string[]>(loadSubs())

function loadSubs(): string[] {
  try {
    const raw = localStorage.getItem(SUBS_KEY)
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

/** 撤一个订阅：本地保存即生效 */
function removeSub(tag: string) {
  const next: string[] = []
  for (const s of subs.value) {
    if (s !== tag) next.push(s)
  }
  subs.value = next
  localStorage.setItem(SUBS_KEY, JSON.stringify(next))
}

onMounted(loadAll)
</script>

<template>
  <div>
    <div class="p-head">
      <h1><span>FORUM / MY ZONE</span>个人中心</h1>
      <span class="sticker">{{ stats.topics + stats.replies }} 条创作</span>
    </div>

    <!-- 资料头卡：方头像 + 身份 + 四格统计 -->
    <div class="box me-card">
      <div class="me-id">
        <!-- 头像即换头像入口：角上黄块"换"字，弹窗见 MeAvatarDialog -->
        <button class="me-ava-btn" type="button" title="点这里换头像" @click="avVisible = true">
          <img class="me-ava" :src="user.avatarByName(user.realName, 72)" :alt="user.realName">
          <span class="me-ava-edit">换</span>
        </button>
        <div>
          <div class="me-name">{{ user.realName }}</div>
          <div class="me-role">{{ roleLabel }}</div>
        </div>
      </div>
      <div class="me-stats">
        <div><b>{{ stats.topics }}</b><span>主题</span></div>
        <div><b>{{ stats.replies }}</b><span>回复</span></div>
        <div><b>{{ stats.likes }}</b><span>获赞</span></div>
        <div><b>{{ stats.collects }}</b><span>收藏</span></div>
      </div>
    </div>

    <!-- 五 tab + 内容（行骨架走 skin.css / 复用组件） -->
    <div class="box">
      <div class="tabs">
        <a v-for="t in TABS" :key="t.key" href="#" :class="{ on: tab === t.key }" @click.prevent="tab = t.key">{{ t.name }}</a>
      </div>

      <template v-if="tab === 'topics'">
        <MePostRows v-if="myTopics.length > 0" :items="myTopics" />
        <div v-else class="empty">还没发过主题——点顶栏"创作新主题"发第一帖</div>
      </template>

      <template v-else-if="tab === 'replies'">
        <MeReplyRows v-if="myReplies.length > 0" :items="myReplies" />
        <div v-else class="empty">还没回过帖——去帖子详情页接一句</div>
      </template>

      <!-- 我的收藏：论坛帖 + 技术推送 两段 -->
      <template v-else-if="tab === 'collections'">
        <div class="sec">论坛帖子</div>
        <MePostRows v-if="myCollections.topics.length > 0" :items="myCollections.topics" />
        <div v-else class="empty empty-s">暂无收藏的帖子——帖子详情页点"收藏"</div>
        <div class="sec">技术推送</div>
        <div v-for="p in myCollections.techPosts" :key="p.id" class="cell">
          <div class="mid">
            <div class="t-title"><router-link to="/feed">{{ p.title }}</router-link></div>
            <div class="t-meta">{{ p.publishAt }} · {{ p.sourceName }} · 浏览 {{ p.viewCount }}</div>
          </div>
        </div>
        <div v-if="myCollections.techPosts.length === 0" class="empty empty-s">暂无收藏的推送——技术交流页点"收藏"</div>
      </template>

      <!-- 我的点赞：帖 + 楼层 两段 -->
      <template v-else-if="tab === 'likes'">
        <div class="sec">帖子</div>
        <MePostRows v-if="myLikes.topics.length > 0" :items="myLikes.topics" />
        <div v-else class="empty empty-s">暂无点赞的帖子</div>
        <div class="sec">楼层</div>
        <MeReplyRows v-if="myLikes.replies.length > 0" :items="myLikes.replies" />
        <div v-else class="empty empty-s">暂无点赞的楼层</div>
      </template>

      <!-- 我的订阅：本地存储，点 × 撤销 -->
      <template v-else>
        <div class="subs-wrap">
          <a v-for="s in subs" :key="s" href="#" class="subchip" @click.prevent="removeSub(s)">{{ s }} <b>×</b></a>
        </div>
        <div v-if="subs.length === 0" class="empty">还没订阅任何标签——去技术交流页订阅</div>
        <div class="subs-tip">
          订阅关系本期存在本地浏览器；点标签上的 × 撤销，技术交流页列表会同步变化。
          <router-link to="/feed">去技术交流 →</router-link>
        </div>
      </template>
    </div>

    <!-- 换头像弹窗 -->
    <MeAvatarDialog v-model="avVisible" />
  </div>
</template>

<style scoped>
/* 页面差异：头卡横排 + 分段题 + 订阅 chips；行与盒皮全走 skin.css */
.me-card { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 20px 22px; margin-bottom: 20px; flex-wrap: wrap; }
.me-id { display: flex; align-items: center; gap: 16px; }
.me-ava-btn { position: relative; padding: 0; border: none; background: none; cursor: pointer; }
.me-ava { width: 72px; height: 72px; border: var(--vio2-bd-acc); object-fit: cover; display: block; }
.me-ava-btn:hover .me-ava { filter: brightness(0.92); }
.me-ava-edit { position: absolute; right: -7px; bottom: -7px; background: var(--vio2-yellow); color: var(--vio2-on-ac); border: 2px solid var(--vio2-shadow); font-size: 10px; font-weight: 900; padding: 1px 6px; transform: rotate(-3deg); }
.me-name { font-size: 24px; font-weight: 900; color: var(--vio2-ink); }
.me-role { margin-top: 5px; font-family: var(--vio2-mono); font-size: 11px; letter-spacing: 0.1em; color: var(--vio2-ink-3); }
.me-stats { display: flex; gap: 12px; flex-wrap: wrap; }
.me-stats > div { min-width: 86px; text-align: center; border: var(--vio2-bd-row); background: var(--vio2-panel-2); padding: 10px 8px; box-shadow: var(--vio2-hard-xs); }
.me-stats b { display: block; font-family: var(--vio2-mono); font-size: 22px; font-weight: 900; color: var(--vio2-ink); }
.me-stats span { font-size: 11px; color: var(--vio2-ink-3); letter-spacing: 0.08em; }
.sec { padding: 12px 16px 8px; font-family: var(--vio2-mono); font-size: 11px; font-weight: 700; letter-spacing: 0.14em; color: var(--vio2-ink-3); border-bottom: 2px solid var(--vio2-row-line); }
.empty-s { padding: 18px 14px; }
.mid { flex: 1; min-width: 0; }
.subs-wrap { display: flex; flex-wrap: wrap; gap: 10px; padding: 18px 16px 6px; }
.subchip { border: var(--vio2-bd-row); background: var(--vio2-panel); padding: 6px 13px; font-size: 13px; font-weight: 800; color: var(--vio2-ink); box-shadow: var(--vio2-hard-xs); }
.subchip b { color: var(--vio2-ink-3); margin-left: 5px; }
.subchip:hover { transform: translate(1px, 1px); box-shadow: 1px 1px 0 var(--vio2-shadow); }
.subs-tip { margin-top: 10px; padding: 12px 16px 16px; font-size: 12px; color: var(--vio2-ink-3); line-height: 1.7; border-top: 2px solid var(--vio2-row-line); }
.subs-tip a { color: var(--vio2-blue); font-weight: 700; }
</style>
