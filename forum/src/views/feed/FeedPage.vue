<script setup lang="ts">
/**
 * 技术交流（每日推送内容流）——与首页同一套行式语言
 * 视觉基准：.agentdocs/design-preview/2026-10-15-forum-redesign/feed.html（新粗野双主题，lzh 已拍板）
 * 数据：T01 列表（category 筛选）、T03 收藏、T04 订阅标签
 * 管理侧（仅超管可见）：T10 录入弹窗、T11 置顶/撤顶、T12 撤回（幂等）
 * 订阅口径（清单 §3 拍板）：订阅关系本期不落库，前端 localStorage 保存；
 *    已订阅任一标签时列表按交集过滤（前端过滤，演示数据量够小）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FEED_CATEGORIES, feedCategoryOf } from '@/config/feedCategories'
import { SOURCE_TYPE_LABEL } from '@/config/labels'
import { useUserStore } from '@/stores/user'
import { apiTechList, apiTechTags, apiCollectTechPost, apiPinTechPost, apiWithdrawTechPost, type TechPostItem } from '@/api/tech'
import { apiHotTopics, type HotTopicItem } from '@/api/forum'
import TechPostDialog from '@/components/TechPostDialog.vue'

const user = useUserStore()
const SUBS_KEY = 'forum_tech_subs'

/** 当前选中分类：'ALL' 为全部 */
const activeCat = ref('ALL')
const feeds = ref<TechPostItem[]>([])
const total = ref(0)
const loading = ref(false)
const hotRank = ref<HotTopicItem[]>([])

/** 订阅标签（on = 已订阅）；订阅关系存 localStorage（本期不落库，清单 §3） */
const allTags = ref<string[]>([])
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

function saveSubs() {
  localStorage.setItem(SUBS_KEY, JSON.stringify(subs.value))
}

/** 切换订阅态：本地保存即生效，列表按交集重算（computed） */
function toggleSub(name: string) {
  const idx = subs.value.indexOf(name)
  if (idx >= 0) subs.value.splice(idx, 1)
  else subs.value.push(name)
  saveSubs()
}

/** 展示列表：已订阅标签非空时按交集过滤（演示期前端过滤，T01 也支持 tag 参数） */
const shownFeeds = computed<TechPostItem[]>(() => {
  if (subs.value.length === 0) return feeds.value
  const list: TechPostItem[] = []
  for (let i = 0; i < feeds.value.length; i++) {
    const f = feeds.value[i]
    let hit = false
    for (let j = 0; j < f.tags.length && !hit; j++) {
      if (subs.value.indexOf(f.tags[j]) >= 0) hit = true
    }
    if (hit) list.push(f)
  }
  return list
})

/** 本周推送统计（右栏）：总数与被收藏最多，从列表数据直接算 */
const maxCollect = computed(() => {
  let m = 0
  for (let i = 0; i < feeds.value.length; i++) {
    if (feeds.value[i].collectCount > m) m = feeds.value[i].collectCount
  }
  return m
})

/** 拉列表（切分类触发）；序号守卫防连点翻分类时旧响应覆盖新结果（同 HomePage） */
let loadSeq = 0
async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const res = await apiTechList({
      category: activeCat.value,
      page: 1,
      size: 50
    })
    if (seq !== loadSeq) return
    feeds.value = res.list
    total.value = res.total
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function switchCat(code: string) {
  activeCat.value = code
  load()
}

/** 操作锁（按条目粒度）：收藏/置顶是天然幂等操作，锁只为防连点白发一堆请求 */
const collectLocks = ref<Record<string, boolean>>({})
const pinLocks = ref<Record<string, boolean>>({})

/** 收藏/取消（T03 天然幂等：收藏关系唯一约束，重复点无副作用） */
async function toggleCollect(f: TechPostItem) {
  if (collectLocks.value[f.id]) return
  collectLocks.value[f.id] = true
  try {
    const target = !f.collected
    const res = await apiCollectTechPost(f.id, target)
    f.collected = res.collected
    f.collectCount += res.collected ? 1 : -1
  } finally {
    collectLocks.value[f.id] = false
  }
}

/** 置顶/撤顶（T11 天然幂等：body 带目标态） */
async function togglePin(f: TechPostItem) {
  if (pinLocks.value[f.id]) return
  pinLocks.value[f.id] = true
  try {
    const target = !f.pinned
    const res = await apiPinTechPost(f.id, target)
    f.pinned = res.pinned
    ElMessage.success(res.pinned ? '已置顶' : '已取消置顶')
    load()
  } finally {
    pinLocks.value[f.id] = false
  }
}

/** 撤回（T12 幂等键：单次确认动作，键随本次点击生成；已撤回的重试会被 9001 挡住） */
async function withdraw(f: TechPostItem) {
  try {
    await ElMessageBox.confirm('撤回后列表不再显示该推送，确定撤回？', '撤回推送', { type: 'warning' })
  } catch {
    return
  }
  await apiWithdrawTechPost(f.id, crypto.randomUUID())
  ElMessage.success('已撤回')
  load()
}

/** 录入弹窗开合 */
const createVisible = ref(false)
function onCreated() {
  load()
}

onMounted(() => {
  load()
  apiTechTags().then((tags) => {
    allTags.value = tags
  })
  apiHotTopics().then((hot) => {
    hotRank.value = hot.slice(0, 3)
  })
})
</script>

<template>
  <div>
    <div class="p-head">
      <h1><span>TECH FEED / 每日推送内容流</span>技术交流</h1>
      <span class="sticker">共 {{ total }} 条推送</span>
    </div>

    <div class="grid">
      <div class="box">
        <!-- 频道行第一行：六分类 chips -->
        <div class="tabs">
          <a href="#" :class="{ on: activeCat === 'ALL' }" @click.prevent="switchCat('ALL')">全部</a>
          <a
            v-for="c in FEED_CATEGORIES"
            :key="c.code"
            href="#"
            :class="{ on: activeCat === c.code }"
            @click.prevent="switchCat(c.code)"
          >{{ c.name }}</a>
        </div>
        <!-- 频道行第二行：订阅标签（点击切换订阅，已订阅紫底，列表按交集过滤） -->
        <div class="tabs2 subs">
          <b>订阅标签</b>
          <a v-for="t in allTags" :key="t" href="#" :class="{ 'sub-on': subs.indexOf(t) >= 0 }" @click.prevent="toggleSub(t)">{{ t }}</a>
          <a v-if="subs.length > 0" href="#" class="clear" @click.prevent="subs = []; saveSubs()">清除订阅</a>
        </div>
        <!-- 管理员入口：录入推送（T10） -->
        <div v-if="user.isAdmin" class="admin-bar">
          <span>共 {{ total }} 条推送 · ADMIN</span>
          <button class="btn-admin" type="button" @click="createVisible = true">＋ 录入推送</button>
        </div>

        <!-- 推送行：左 48px 分类色块字 + 标题/摘要单行截断/mono meta + 右收藏数块 -->
        <div v-for="f in shownFeeds" :key="f.id" class="cell">
          <span class="cat" :class="feedCategoryOf(f.category).classKey">{{ feedCategoryOf(f.category).short }}</span>
          <div class="mid">
            <div class="t-title">
              <span v-if="f.pinned" class="pin">置顶</span>
              <a href="#" @click.prevent>{{ f.title }}</a>
            </div>
            <div class="f-sum">{{ f.summary }}</div>
            <div class="t-meta">
              <span class="node">{{ feedCategoryOf(f.category).name }}</span>{{ SOURCE_TYPE_LABEL[f.sourceType] ?? f.sourceType }} · {{ f.publishAt }} · 阅读 {{ f.viewCount }} · {{ f.tags.join(' · ') }}
              <!-- 管理操作（仅超管）：置顶/撤顶、撤回 -->
              <span v-if="user.isAdmin" class="mg">
                <a href="#" @click.prevent="togglePin(f)">{{ f.pinned ? '撤顶' : '置顶' }}</a>
                <a href="#" @click.prevent="withdraw(f)">撤回</a>
              </span>
            </div>
          </div>
          <span class="collect" :class="{ on: f.collected }" role="button" @click="toggleCollect(f)">{{ f.collected ? '已收藏' : '⭐' }} {{ f.collectCount }}</span>
        </div>
        <div v-if="!loading && shownFeeds.length === 0" class="empty">这个分类下暂时没有推送</div>
      </div>

      <div class="side">
        <div class="box">
          <div class="box-h"><b>今日热议主题</b><span>HOT / 24H</span></div>
          <div class="bd">
            <ul class="hot">
              <li v-for="(h, i) in hotRank" :key="h.id">
                <span class="rk">{{ String(i + 1).padStart(2, '0') }}</span>
                <router-link :to="`/topic/${h.id}`">{{ h.title }}</router-link>
              </li>
            </ul>
          </div>
        </div>
        <div class="box">
          <div class="box-h"><b>推送统计</b><span>WEEK</span></div>
          <div class="bd stat">
            <div><span>推送总数</span><b>{{ total }}</b></div>
            <div><span>单篇被收藏最多</span><b>{{ maxCollect }}</b></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 管理员录入推送（T10，幂等提交在弹窗内处理） -->
    <TechPostDialog v-model="createVisible" @created="onCreated" />
  </div>
</template>

<style scoped>
/* 页面差异：双列网格 + 分类色块 + 订阅 chips + 管理条 + 收藏钮；行骨架走 skin.css */
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 22px; align-items: start; }
.mid { flex: 1; min-width: 0; }

/* 订阅标签 chips（覆盖 skin.css 的纯文字链形态） */
.subs b { font-weight: 700; }
.subs a { border: var(--vio2-bd-row); background: var(--vio2-panel); color: var(--vio2-ink-2); padding: 2px 10px; font-weight: 700; box-shadow: 2px 2px 0 var(--vio2-shadow); }
.subs a:hover { color: var(--vio2-ink); transform: translate(1px, 1px); box-shadow: 1px 1px 0 var(--vio2-shadow); }
.subs a.sub-on { background: var(--vio2-lilac); color: var(--vio2-on-ac); border-color: var(--vio2-shadow); }
.subs a.clear { border: none; background: none; box-shadow: none; text-decoration: underline; margin-left: auto; }

/* 管理条 */
.admin-bar { display: flex; align-items: center; padding: 9px 16px; border-bottom: 2px solid var(--vio2-row-line); font-family: var(--vio2-mono); font-size: 11px; color: var(--vio2-ink-3); letter-spacing: 0.08em; }
.btn-admin { margin-left: auto; border: var(--vio2-bd-row); background: var(--vio2-blue); color: #FFF; font: inherit; font-size: 12px; font-weight: 900; padding: 5px 14px; cursor: pointer; box-shadow: var(--vio2-hard-xs); }
.btn-admin:hover { transform: translate(2px, 2px); box-shadow: 1px 1px 0 var(--vio2-shadow); }

/* 分类色块：六分类对应五强调色 + 面板灰（classKey 与 feedCategories.ts 一一对应） */
.cat { width: 48px; height: 48px; flex: none; border: var(--vio2-bd-acc); display: grid; place-items: center; font-size: 15px; font-weight: 900; color: var(--vio2-on-ac); }
.k-ee { background: var(--vio2-blue); }
.k-mo { background: var(--vio2-green); }
.k-ai { background: var(--vio2-lilac); }
.k-vd { background: var(--vio2-pink); }
.k-dev { background: var(--vio2-yellow); }
.k-etc { background: var(--vio2-panel-2); color: var(--vio2-ink-2); }

.f-sum { font-size: 13px; color: var(--vio2-ink-2); font-weight: 600; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mg { margin-left: 10px; }
.mg a { color: var(--vio2-ink-3); text-decoration: underline; margin-right: 9px; }
.mg a:hover { color: var(--vio2-pink); }

/* 收藏钮：描边块，已收藏黄底 */
.collect { flex: none; align-self: center; border: var(--vio2-bd-row); background: var(--vio2-panel); color: var(--vio2-ink-2); font-family: var(--vio2-mono); font-size: 12px; font-weight: 700; padding: 4px 10px; cursor: pointer; box-shadow: var(--vio2-hard-xs); }
.collect.on { background: var(--vio2-yellow); color: var(--vio2-on-ac); border-color: var(--vio2-shadow); }
</style>
