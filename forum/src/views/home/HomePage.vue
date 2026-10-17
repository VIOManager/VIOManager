<script setup lang="ts">
/**
 * 论坛首页：频道行（版块 + 热门标签）并入主列盒 + 行式主题列表 + 右栏
 * 视觉基准：.agentdocs/design-preview/2026-10-15-forum-redesign/home.html（新粗野双主题，lzh 已拍板）
 * 数据：F01 帖子列表（board/sort/keyword/分页）、F02 今日热议、F03 社区统计
 * 搜索：顶栏搜索框回车跳 ?q=关键词，本页 watch route.query 联动查询
 * 标签行（接口方案回迁）：按当前版块全量主题单独聚合（size=50 封顶），
 *    翻页/排序/关键词变化不影响标签行——导航入口保持稳定；
 *    点击即按该关键词搜索（F01 keyword 含标题与标签）
 */
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BOARDS, boardName } from '@/config/boards'
import { avatarOf } from '@/config/avatars'
import { apiTopicList, apiHotTopics, apiForumStats, type TopicItem, type HotTopicItem } from '@/api/forum'

const mainSite = import.meta.env.VITE_MAIN_SITE_URL ?? 'http://localhost:5174'
const PAGE_SIZE = 10

/** 当前选中版块：'ALL' 为"全部"（boards 之外的伪选项） */
const activeBoard = ref<string>('ALL')
const activeSort = ref<'latest' | 'hot'>('latest')
const SORTS = [
  { code: 'latest', name: '最新' },
  { code: 'hot', name: '最热' }
] as const

/**
 * 标签行：按当前版块全量主题聚合（独立请求，size=50 = F01 页上限封顶，演示量全覆盖）。
 * 独立于主列表的翻页/排序/关键词——标签是导航入口，不能跟着当前页内容漂移
 */
const allTags = ref<string[]>([])
async function loadTags() {
  const res = await apiTopicList({
    // 'ALL' 为前端"全部"伪选项，同 load() 的归一口径
    board: activeBoard.value === 'ALL' ? '' : activeBoard.value,
    page: 1,
    size: 50
  })
  const set = new Set<string>()
  for (const t of res.list) {
    for (const g of t.tags) set.add(g)
  }
  allTags.value = Array.from(set)
}

/** 点击标签 = 关键词搜索（复用顶栏搜索链路：跳 ?q= 由 watch 联动查询） */
const router = useRouter()
function searchTag(tag: string) {
  router.push({ path: '/', query: tag ? { q: tag } : {} })
}

const topics = ref<TopicItem[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

/** 今日热议（侧栏）与社区统计 */
const hotRank = ref<HotTopicItem[]>([])
const stats = ref({ memberCount: 0, topicCount: 0, replyCount: 0 })

/**
 * 拉帖子列表（切版块/排序/翻页/搜索词变化都走这里）
 * ⚠️ 响应竞态守卫：快速连点翻页/切版块会并发多个请求，晚到的旧响应
 *    会覆盖新结果（显示错页）——序号对不上的响应直接丢弃，只认最后一次
 */
let loadSeq = 0
async function load() {
  const seq = ++loadSeq
  loading.value = true
  try {
    const res = await apiTopicList({
      // 'ALL' 是前端"全部"伪选项，后端只认真实版块码（未知码按空结果处理），上线路前归一为空串
      board: activeBoard.value === 'ALL' ? '' : activeBoard.value,
      sort: activeSort.value,
      keyword: keyword.value,
      page: page.value,
      size: PAGE_SIZE
    })
    if (seq !== loadSeq) return
    topics.value = res.list
    total.value = res.total
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

/** 顶栏搜索词：route.query.q（顶栏搜索回车后跳到本页带参） */
const route = useRoute()
const keyword = ref('')

function applyQuery() {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  page.value = 1
  load()
}

// 首次进页 + 顶栏搜索跳转（q 或发帖后的时间戳 t 变化）都触发重查
watch(() => route.query, applyQuery, { immediate: true })

function switchBoard(code: string) {
  activeBoard.value = code
  page.value = 1
  load()
  // 标签行跟着版块走（与翻页/排序/关键词无关）
  loadTags()
}

function switchSort(code: 'latest' | 'hot') {
  activeSort.value = code
  page.value = 1
  load()
}

function turnPage(delta: number) {
  const next = page.value + delta
  if (next < 1 || (next - 1) * PAGE_SIZE >= total.value) return
  page.value = next
  load()
}

async function loadSide() {
  hotRank.value = await apiHotTopics()
  stats.value = await apiForumStats()
}
loadSide()
loadTags()
</script>

<template>
  <div>
    <!-- 页头：mono 眉行 + 大标题 + 贴纸（数据取真实热榜条数） -->
    <div class="p-head">
      <h1><span>FORUM / 团队技术集散地</span>团队论坛</h1>
      <span class="sticker">今日热议 {{ hotRank.length }} 帖</span>
    </div>

    <div class="grid">
      <!-- 主列盒：频道两行在盒内，行下即主题列表 -->
      <div class="box">
        <div class="tabs">
          <a href="#" :class="{ on: activeBoard === 'ALL' }" @click.prevent="switchBoard('ALL')">全部</a>
          <a
            v-for="b in BOARDS"
            :key="b.code"
            href="#"
            :class="{ on: activeBoard === b.code }"
            @click.prevent="switchBoard(b.code)"
          >{{ b.name }}</a>
          <span class="right">
            <a
              v-for="s in SORTS"
              :key="s.code"
              href="#"
              :class="{ on: activeSort === s.code }"
              @click.prevent="switchSort(s.code)"
            >{{ s.name }}</a>
          </span>
        </div>
        <div class="tabs2">
          <a href="#" :class="{ on: !keyword }" title="清除关键词，显示全部主题" @click.prevent="searchTag('')">全部</a>
          <a v-for="t in allTags" :key="t" href="#" :class="{ on: keyword === t }" @click.prevent="searchTag(t)">#{{ t }}</a>
          <span v-if="keyword" class="kw">关键词：{{ keyword }}</span>
        </div>

        <!-- 主题行：48px 方头像 + 标题 + mono meta + 回复数块（热帖黄底） -->
        <div v-for="t in topics" :key="t.id" class="cell">
          <img class="ava" :src="avatarOf(t.author, 48)" :alt="t.author">
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
        <div v-if="!loading && topics.length === 0" class="empty">这个筛选下暂时没有主题</div>

        <!-- 简版分页：‹ 页码信息 › -->
        <div v-if="total > PAGE_SIZE" class="pager">
          <a href="#" :class="{ off: page <= 1 }" @click.prevent="turnPage(-1)">‹</a>
          <span class="info">PAGE {{ page }} / {{ Math.ceil(total / PAGE_SIZE) }} · {{ total }} TOPICS</span>
          <a href="#" :class="{ off: page * PAGE_SIZE >= total }" @click.prevent="turnPage(1)">›</a>
        </div>
      </div>

      <div class="side">
        <div class="box brand">
          <div class="bd">
            <div class="slogan">VIO 论坛 = 团队的技术集散地</div>
            <div class="sub2">培训笔记、排障记录、复盘总结都发在这里</div>
            <a class="back" :href="mainSite">返回主站 →</a>
          </div>
        </div>
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
          <div class="box-h"><b>社区运行状况</b><span>STATS</span></div>
          <div class="bd stat">
            <div><span>注册成员</span><b>{{ stats.memberCount }}</b></div>
            <div><span>主题</span><b>{{ stats.topicCount }}</b></div>
            <div><span>回复</span><b>{{ stats.replyCount }}</b></div>
          </div>
        </div>
        <div class="box">
          <div class="box-h"><b>全部版块</b><span>NODES</span></div>
          <div class="bd nodes">
            <a v-for="b in BOARDS" :key="b.code" href="#" @click.prevent="switchBoard(b.code)">{{ b.name }}</a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 页面差异：双列网格 + 主站链接；其余皮肤全走 skin.css 共享组件 */
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 22px; align-items: start; }
.mid { flex: 1; min-width: 0; }
.kw { margin-left: auto; }
</style>
