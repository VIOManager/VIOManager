<script setup lang="ts">
/**
 * 论坛首页：频道行（版块 + 热门标签）并入主列盒 + 行式主题列表 + 右栏
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/index.html（v3 样张，lzh 已过目）
 * 数据：F01 帖子列表（board/sort/keyword/分页）、F02 今日热议、F03 社区统计
 * 搜索：顶栏搜索框回车跳 ?q=关键词，本页 watch route.query 联动查询
 * TODO(联调-论坛清单)：F01–F03 路径与字段为前端预设，清单出稿后对齐；
 *    热门标签行暂为静态展示（标签接口待清单）
 */
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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

/** 第二行热门标签；TODO(联调-论坛清单)：由标签接口下发 */
const hotTags = ['STM32', '电机控制', 'CAD', 'GLM', 'PCB', 'RoboMaster', '招新']

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
      board: activeBoard.value,
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
</script>

<template>
  <div class="grid">
    <!-- 主列白盒：频道两行在盒内，行下即主题列表（不另起盒子） -->
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
        <a v-for="t in hotTags" :key="t" href="#" @click.prevent>{{ t }}</a>
        <span v-if="keyword" class="kw">关键词：{{ keyword }}</span>
      </div>

      <!-- 主题行：48px 真人头像 + 标题 + meta + 回复数胶囊 -->
      <div v-for="t in topics" :key="t.id" class="cell">
        <img class="ava" :src="avatarOf(t.author, 48)" :alt="t.author">
        <div class="mid">
          <div class="t-title">
            <span v-if="t.pinned" class="pin">置顶</span>
            <router-link :to="`/topic/${t.id}`">{{ t.title }}</router-link>
          </div>
          <div class="t-meta">
            <span class="node">{{ boardName(t.board) }}</span><b>{{ t.author }}</b> · {{ t.lastActiveAt }} · 最后回复来自 <b>{{ t.lastReplyBy }}</b>
          </div>
        </div>
        <span class="pill">{{ t.replyCount }}</span>
      </div>
      <div v-if="!loading && topics.length === 0" class="empty">这个筛选下暂时没有主题</div>

      <!-- 简版分页：上一页 / 页码 / 下一页 -->
      <div v-if="total > PAGE_SIZE" class="pager">
        <a href="#" :class="{ off: page <= 1 }" @click.prevent="turnPage(-1)">‹ 上一页</a>
        <span>第 {{ page }} / {{ Math.ceil(total / PAGE_SIZE) }} 页</span>
        <a href="#" :class="{ off: page * PAGE_SIZE >= total }" @click.prevent="turnPage(1)">下一页 ›</a>
      </div>
    </div>

    <div class="side">
      <div class="box brand-box">
        <div class="slogan">VIO 论坛 = 团队的技术集散地</div>
        <div class="sub">培训笔记、排障记录、复盘总结都发在这里</div>
        <a class="back" :href="mainSite">返回主站 →</a>
      </div>
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
        <h5>社区运行状况</h5>
        <div class="stat">
          <div><span>注册成员</span><b>{{ stats.memberCount }}</b></div>
          <div><span>主题</span><b>{{ stats.topicCount }}</b></div>
          <div><span>回复</span><b>{{ stats.replyCount }}</b></div>
        </div>
      </div>
      <div class="box">
        <h5>全部版块</h5>
        <div class="nodes">
          <a v-for="b in BOARDS" :key="b.code" href="#" @click.prevent="switchBoard(b.code)">{{ b.name }}</a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 20px; align-items: start; }
.box { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); }

/* 频道行：第一行版块 + 排序，第二行热门标签 */
.tabs { padding: 10px 14px 8px; border-bottom: 1px solid var(--line); }
.tabs a { font-size: 13px; color: var(--body); padding: 3px 9px; border-radius: 4px; margin-right: 2px; }
.tabs a.on { background: var(--tab-dark); color: #FFF; }
.tabs a:not(.on):hover { background: #F2F2F2; color: #000; }
.tabs .right { float: right; }
.tabs .right a { color: var(--fade); font-size: 12.5px; }
.tabs .right a:hover { color: var(--ink); }
.tabs2 { padding: 8px 14px; border-bottom: 1px solid var(--line); font-size: 12.5px; display: flex; align-items: center; }
.tabs2 a { color: var(--fade); margin-right: 14px; }
.tabs2 a:hover { color: var(--ink); }
.tabs2 .kw { margin-left: auto; color: var(--meta); }

/* 主题行：行高约 66px，行间 1px 浅线，无卡片感 */
.cell { display: flex; align-items: center; gap: 13px; padding: 10px 14px; border-bottom: 1px solid var(--line); }
.cell:last-child { border-bottom: none; }
.ava { width: 48px; height: 48px; flex: none; border-radius: var(--radius-ava); object-fit: cover; }
.mid { flex: 1; min-width: 0; }
.t-title { font-size: 16px; line-height: 1.3; font-weight: 400; }
.t-title a { color: var(--ink); }
.t-title a:hover { color: #000; text-decoration: underline; }
.pin { font-size: 11px; color: var(--fade); border: 1px solid #DDD; border-radius: 3px; padding: 0 5px; margin-right: 6px; vertical-align: 1px; }
.t-meta { margin-top: 5px; font-size: 12px; color: var(--fade); }
.node { background: #F5F5F5; color: var(--fade); border-radius: 10px; padding: 1px 8px; margin-right: 8px; }
.node:hover { background: #EBEBEB; color: var(--body); }
.t-meta b { color: var(--meta); font-weight: 600; }
.pill { flex: none; background: var(--pill); color: #FFF; font-size: 12px; border-radius: 10px; padding: 1px 9px; margin-right: 4px; }
.empty { padding: 30px 14px; font-size: 13px; color: var(--fade); text-align: center; }
.pager { display: flex; align-items: center; justify-content: center; gap: 18px; padding: 12px 0; font-size: 12.5px; color: var(--fade); }
.pager a { color: var(--body); }
.pager a:hover { color: #000; }
.pager a.off { color: #D0D0D0; pointer-events: none; }

/* 右栏 */
.side .box { padding: 12px 14px; margin-bottom: 18px; }
.brand-box .slogan { font-size: 14px; color: var(--ink); font-weight: 600; }
.brand-box .sub { font-size: 12.5px; color: var(--fade); margin: 4px 0 12px; }
.brand-box .back { font-size: 12px; color: var(--fade); }
.brand-box .back:hover { color: var(--ink); }
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
.nodes a { display: inline-block; font-size: 12px; background: #F5F5F5; color: var(--meta); border-radius: 10px; padding: 2px 10px; margin: 0 6px 6px 0; }
.nodes a:hover { background: #EBEBEB; color: var(--ink); }
</style>
