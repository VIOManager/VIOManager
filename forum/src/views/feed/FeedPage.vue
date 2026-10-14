<script setup lang="ts">
/**
 * 技术交流（每日推送内容流）——与首页同一套行式语言
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/feed.html（v3 样张，lzh 已过目）
 * 数据：T01 列表（category 筛选）、T03 收藏、T04 订阅标签
 * 管理侧（仅超管可见）：T10 录入弹窗、T11 置顶/撤顶、T12 撤回（幂等）
 * 订阅口径（清单 §3 拍板）：订阅关系本期不落库，前端 localStorage 保存；
 *    已订阅任一标签时列表按交集过滤（前端过滤，演示数据量够小）
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FEED_CATEGORIES, feedCategoryOf } from '@/config/feedCategories'
import { avatarOf } from '@/config/avatars'
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
  <div class="grid">
    <div class="box">
      <!-- 频道行第一行：七分类 tab + 右端来源说明 -->
      <div class="tabs">
        <a href="#" :class="{ on: activeCat === 'ALL' }" @click.prevent="switchCat('ALL')">全部</a>
        <a
          v-for="c in FEED_CATEGORIES"
          :key="c.code"
          href="#"
          :class="{ on: activeCat === c.code }"
          @click.prevent="switchCat(c.code)"
        >{{ c.name }}</a>
        <span class="right">管理员录入 / 外部收录 / AI 摘要</span>
      </div>
      <!-- 频道行第二行：订阅标签（点击切换订阅，已订阅的列表按交集过滤） -->
      <div class="tabs2">
        <b>订阅标签</b>
        <a v-for="t in allTags" :key="t" href="#" :class="{ 'sub-on': subs.indexOf(t) >= 0 }" @click.prevent="toggleSub(t)">{{ t }}</a>
        <a v-if="subs.length > 0" href="#" class="clear" @click.prevent="subs = []; saveSubs()">清除订阅</a>
      </div>
      <!-- 管理员入口：录入推送（T10） -->
      <div v-if="user.isAdmin" class="admin-bar">
        <span>共 {{ total }} 条推送</span>
        <button class="btn-admin" type="button" @click="createVisible = true">+ 录入推送</button>
      </div>

      <!-- 推送行：左 48px 分类色块字 + 标题/摘要单行截断/meta + 右收藏数胶囊 -->
      <div v-for="f in shownFeeds" :key="f.id" class="cell">
        <span class="cat" :class="feedCategoryOf(f.category).classKey">{{ feedCategoryOf(f.category).short }}</span>
        <div class="mid">
          <div class="f-title">
            <span v-if="f.pinned" class="pin">置顶</span>
            <a href="#" @click.prevent>{{ f.title }}</a>
          </div>
          <div class="f-sum">{{ f.summary }}</div>
          <div class="f-meta">
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
        <h5>今日热议主题</h5>
        <ul class="hot">
          <li v-for="h in hotRank" :key="h.id">
            <img class="hava" :src="avatarOf(h.author, 24)" :alt="h.author">
            <router-link :to="`/topic/${h.id}`">{{ h.title }}</router-link>
          </li>
        </ul>
      </div>
      <div class="box">
        <h5>推送统计</h5>
        <div class="stat">
          <div><span>推送总数</span><b>{{ total }}</b></div>
          <div><span>单篇被收藏最多</span><b>{{ maxCollect }}</b></div>
        </div>
      </div>
    </div>

    <!-- 管理员录入推送（T10，幂等提交在弹窗内处理） -->
    <TechPostDialog v-model="createVisible" @created="onCreated" />
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 20px; align-items: start; }
.box { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); }

/* 频道行 */
.tabs { padding: 10px 14px 8px; border-bottom: 1px solid var(--line); }
.tabs a { font-size: 13px; color: var(--body); padding: 3px 9px; border-radius: 4px; margin-right: 2px; }
.tabs a.on { background: var(--tab-dark); color: #FFF; }
.tabs a:not(.on):hover { background: #F2F2F2; color: #000; }
.tabs .right { float: right; font-size: 12px; color: var(--fade); padding-top: 4px; }
.tabs2 { padding: 8px 14px; border-bottom: 1px solid var(--line); font-size: 12px; color: var(--fade); }
.tabs2 b { font-weight: 400; margin-right: 10px; }
.tabs2 a { color: var(--fade); margin-right: 12px; }
.tabs2 a.sub-on { color: var(--ink); font-weight: 600; }
.tabs2 a:hover { color: var(--ink); }
.tabs2 a.clear { margin-left: auto; text-decoration: underline; }

/* 管理条 */
.admin-bar { display: flex; align-items: center; padding: 7px 14px; border-bottom: 1px solid var(--line); font-size: 12px; color: var(--fade); }
.btn-admin { margin-left: auto; font-size: 12px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 3px 12px; cursor: pointer; }
.btn-admin:hover { border-color: #999; }

/* 推送行 */
.cell { display: flex; gap: 13px; padding: 11px 14px; border-bottom: 1px solid var(--line); }
.cell:last-child { border-bottom: none; }
.cat { width: 48px; height: 48px; flex: none; border-radius: var(--radius-ava); display: grid; place-items: center; font-size: 15px; font-weight: 600; }
/* 六分类浅底深字配色（classKey 与 feedCategories.ts 一一对应） */
.k-ee { background: #DCE7F5; color: #3D5A80; }
.k-mo { background: #E2EFDA; color: #4F6F4A; }
.k-ai { background: #E8E2F5; color: #5A4A8A; }
.k-vd { background: #F5D9E0; color: #8A3D5A; }
.k-dev { background: #DDEFF0; color: #3D6E80; }
.k-etc { background: #EFEFEF; color: #666; }
.mid { flex: 1; min-width: 0; }
.f-title { font-size: 16px; line-height: 1.3; font-weight: 400; }
.f-title a { color: var(--ink); }
.f-title a:hover { color: #000; text-decoration: underline; }
.pin { font-size: 11px; color: var(--fade); border: 1px solid #DDD; border-radius: 3px; padding: 0 5px; margin-right: 6px; vertical-align: 1px; }
.f-sum { font-size: 13px; color: #888; margin-top: 3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.f-meta { margin-top: 4px; font-size: 12px; color: var(--fade); }
.node { background: #F5F5F5; color: var(--fade); border-radius: 10px; padding: 1px 8px; margin-right: 8px; }
.mg { margin-left: 10px; }
.mg a { color: #B0B0B0; margin-right: 8px; text-decoration: underline; }
.mg a:hover { color: var(--ink); }
.collect { flex: none; align-self: center; background: #F5F5F5; color: var(--meta); font-size: 12px; border-radius: 10px; padding: 1px 9px; cursor: pointer; }
.collect.on { background: var(--pill); color: #FFF; }
.empty { padding: 30px 14px; font-size: 13px; color: var(--fade); text-align: center; }

/* 右栏 */
.side .box { padding: 12px 14px; margin-bottom: 18px; }
.side h5 { font-size: 12.5px; color: var(--fade); font-weight: 400; border-bottom: 1px solid var(--line-soft); padding-bottom: 8px; margin-bottom: 4px; }
.hot { margin: 0; padding: 0; }
.hot li { list-style: none; display: flex; gap: 9px; align-items: flex-start; padding: 8px 0; border-bottom: 1px solid #F4F4F4; }
.hot li:last-child { border: none; padding-bottom: 2px; }
.hava { width: 24px; height: 24px; flex: none; border-radius: var(--radius-ava); object-fit: cover; margin-top: 1px; }
.hot a { font-size: 12.5px; color: var(--body); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.hot a:hover { color: #000; }
.stat div { display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #F7F7F7; font-size: 12.5px; color: var(--fade); }
.stat div:last-child { border: none; }
.stat b { color: var(--body); font-weight: 600; }
</style>
