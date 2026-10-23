<script setup lang="ts">
/**
 * 帖子详情（编排层）：主楼 + 右栏在本页，楼层与回复框拆到 ReplyList / ReplyEditor（320 行超铁律拆分件）。
 * 数据：F04 详情（浏览计数服务端 +1）、F05 楼层（一级分页 + 二级 children）、F02 本版热议
 * 写操作：F07 回帖（ReplyEditor）、F08/F09 主楼点赞收藏、F11-F14 管理档（AdminDialog 统一弹窗）
 * 契约基准：backend/vio-forum 控制器（F01-F14 已实现）；LOCKED 禁回 9101 后端兜底、前端先拦
 * 附件下载依赖文件服务方案，点击仅提示
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { boardName } from '@/config/boards'
import { useUserStore } from '@/stores/user'
import ReplyList from '@/components/ReplyList.vue'
import ReplyEditor from '@/components/ReplyEditor.vue'
import AdminDialog, { type AdminTarget } from '@/components/AdminDialog.vue'
import {
  apiTopicDetail, apiReplyList, apiLikeTopic, apiCollectTopic, apiHotTopics,
  type TopicDetail, type ReplyItem, type HotTopicItem
} from '@/api/forum'

const route = useRoute()
const router = useRouter()
/** 头像统一走 store（本人已换自定义头像时主楼/楼层也是新的） */
const user = useUserStore()
const topicId = String(route.params.id)

const detail = ref<TopicDetail | null>(null)
const floors = ref<ReplyItem[]>([])
const boardHot = ref<HotTopicItem[]>([])

/** 楼层分页（后端 F05 一级分页、size 上限 100） */
const REPLY_PAGE_SIZE = 20
const replyPage = ref(1)
const replyTotal = ref(0)

/** LOCKED 主题禁回（detail.status 已透出，ReplyEditor 置灰 + 后端 9101 兜底） */
const locked = computed(() => detail.value?.status === 'LOCKED')

async function loadDetail() {
  try {
    detail.value = await apiTopicDetail(topicId)
  } catch {
    // 帖子不存在/被隐藏：拦截器已提示；必须清空——管理回调靠 !detail 识别"已不可见"并跳首页
    detail.value = null
  }
}

/**
 * 拉当前页楼层。⚠️ 响应竞态守卫：快速翻页并发时晚到的旧响应会覆盖新页——
 * 序号对不上的响应直接丢弃，只认最后一次（同 HomePage 列表口径）
 */
let floorSeq = 0
async function loadFloors() {
  const seq = ++floorSeq
  const res = await apiReplyList(topicId, { page: replyPage.value, size: REPLY_PAGE_SIZE })
  if (seq !== floorSeq) return
  floors.value = res.list
  replyTotal.value = res.total
  if (detail.value) detail.value.replyCount = res.total
}

/** 楼层翻页（上一页/下一页；首页/末页由 pager 的 off 态拦住） */
function turnReplyPage(delta: number) {
  const next = replyPage.value + delta
  if (next < 1 || (next - 1) * REPLY_PAGE_SIZE >= replyTotal.value) return
  replyPage.value = next
  loadFloors()
}

onMounted(() => {
  loadDetail()
  loadFloors()
  loadBoardHot()
})

/** 右栏本版热议：全站热议里筛本版，不足三条就补全站的（演示期数据量小） */
async function loadBoardHot() {
  const hot = await apiHotTopics()
  const list: HotTopicItem[] = []
  for (let i = 0; i < hot.length; i++) {
    if (detail.value && hot[i].id !== detail.value.id) list.push(hot[i])
  }
  boardHot.value = list.slice(0, 4)
}

/** 主楼操作锁：点赞/收藏天然幂等，锁只为防连点白发一堆请求 */
const likePending = ref(false)
const collectPending = ref(false)

async function toggleLike() {
  if (likePending.value || !detail.value) return
  likePending.value = true
  try {
    const target = !detail.value.liked
    const res = await apiLikeTopic(detail.value.id, target)
    detail.value.liked = res.liked
    detail.value.likeCount = res.likeCount
  } finally {
    likePending.value = false
  }
}

async function toggleCollect() {
  if (collectPending.value || !detail.value) return
  collectPending.value = true
  try {
    const target = !detail.value.collected
    const res = await apiCollectTopic(detail.value.id, target)
    detail.value.collected = res.collected
  } finally {
    collectPending.value = false
  }
}

/* ── 回复框目标态：二级回复（parentReplyId）与引用（quoteReplyId）互斥 ── */
const parentReply = ref<ReplyItem | null>(null)
const quoteReply = ref<ReplyItem | null>(null)
const editorRef = ref<InstanceType<typeof ReplyEditor> | null>(null)

function onFloorReplyTo(f: ReplyItem) {
  parentReply.value = f
  quoteReply.value = null
  editorRef.value?.focus()
}

function onFloorQuote(f: ReplyItem) {
  quoteReply.value = f
  parentReply.value = null
  editorRef.value?.focus()
}

/** 回复成功：一级新楼跳末页看自己的回复；二级挂当前页父楼下，留在本页即可 */
function onEditorReplied() {
  const wasChild = !!parentReply.value
  parentReply.value = null
  quoteReply.value = null
  if (!wasChild) {
    replyPage.value = Math.max(1, Math.ceil((replyTotal.value + 1) / REPLY_PAGE_SIZE))
  }
  loadFloors()
}

/* ── 管理档（两档）：主楼与楼层的"管理"都进统一弹窗 ── */
const adminOpen = ref(false)
const adminTarget = ref<AdminTarget | null>(null)

function openTopicAdmin() {
  if (!detail.value) return
  adminTarget.value = {
    type: 'topic',
    id: detail.value.id,
    label: `主题 #${detail.value.id}`,
    status: detail.value.status,
    title: detail.value.title,
    // 正文回填按存储契约单 \n 分段（方案 §3.5；"空行分段"是 mock 演示层口径，真实后端不认）
    bodyText: detail.value.body.join('\n'),
    tags: detail.value.tags
  }
  adminOpen.value = true
}

function onFloorAdmin(f: ReplyItem) {
  adminTarget.value = { type: 'reply', id: f.id, label: `#${f.floorNo} 楼`, status: f.status, text: f.text }
  adminOpen.value = true
}

/** 管理操作完成：重拉详情与楼层；主题被隐藏后详情 9102，回首页（含隐藏列表里可恢复） */
async function onAdminDone() {
  await loadDetail()
  if (!detail.value) {
    router.push('/')
    return
  }
  loadFloors()
}

/** 附件点击：下载依赖文件服务，联调前提示 */
function attachTip() {
  ElMessage.info('附件下载待论坛文件服务接入后开放')
}
</script>

<template>
  <div>
    <div v-if="detail" class="crumb2"><router-link to="/">首页</router-link> › <a href="#" @click.prevent>{{ boardName(detail.board) }}</a> › 主题 #{{ detail.id }}</div>
    <div class="grid">
      <div>
        <!-- 主楼：左 73px 方头像 + 标题/meta；管理档追加"管理"入口（统一弹窗） -->
        <div v-if="detail" class="box post">
          <div class="in">
            <div class="p-top">
              <img class="pava" :src="user.avatarByName(detail.author, 73)" :alt="detail.author">
              <div class="ph-main">
                <h1><span v-if="detail.pinned" class="pin">置顶</span>{{ detail.title }}</h1>
                <div class="p-meta"><b>{{ detail.author }}</b> · <span class="node">{{ boardName(detail.board) }}</span> · {{ detail.publishAt }} · 阅读 {{ detail.viewCount }}</div>
              </div>
            </div>
            <div class="p-body">
              <p v-for="(p, i) in detail.body" :key="i">{{ p }}</p>
            </div>
            <div v-if="detail.attach.length > 0" class="attach">
              <div v-for="a in detail.attach" :key="a.name">
                <a href="#" @click.prevent="attachTip">{{ a.name }}</a>
                <span class="gray">{{ a.size }} · 下载 {{ a.downloads }}</span>
              </div>
            </div>
            <div class="ops">
              <button type="button" class="chipbtn" :class="{ on: detail.liked }" @click="toggleLike">{{ detail.liked ? '👍 已点赞' : '👍 点赞' }} {{ detail.likeCount }}</button>
              <button type="button" class="chipbtn" :class="{ on: detail.collected }" @click="toggleCollect">{{ detail.collected ? '⭐ 已收藏' : '⭐ 收藏' }}</button>
              <button type="button" class="chipbtn" @click="editorRef?.focus()">↩ 回复</button>
              <button v-if="user.isAdmin" type="button" class="chipbtn" @click="openTopicAdmin">⚙ 管理</button>
            </div>
          </div>
        </div>

        <!-- 回复盒：黑条头行 + 楼层列表（拆分件，含二级与楼层管理入口） -->
        <div class="box replies">
          <div class="box-h"><b>{{ replyTotal }} 条回复 · 按时间正序</b><span><a href="#reply" class="h-link">添加新回复 ↓</a></span></div>
          <ReplyList :floors="floors" :is-admin="user.isAdmin" @reply-to="onFloorReplyTo" @quote="onFloorQuote" @admin="onFloorAdmin" />
          <div v-if="replyTotal > REPLY_PAGE_SIZE" class="pager">
            <a href="#" :class="{ off: replyPage <= 1 }" @click.prevent="turnReplyPage(-1)">‹</a>
            <span class="info">PAGE {{ replyPage }} / {{ Math.max(1, Math.ceil(replyTotal / REPLY_PAGE_SIZE)) }} · {{ replyTotal }} FLOORS</span>
            <a href="#" :class="{ off: replyPage * REPLY_PAGE_SIZE >= replyTotal }" @click.prevent="turnReplyPage(1)">›</a>
          </div>
        </div>

        <!-- 回复框（拆分件：locked 置灰 / 二级与引用目标互斥展示） -->
        <ReplyEditor
          ref="editorRef"
          :topic-id="topicId"
          :locked="locked"
          :parent-target="parentReply"
          :quote-target="quoteReply"
          @replied="onEditorReplied"
          @clear-parent="parentReply = null"
          @clear-quote="quoteReply = null"
        />
      </div>

      <div class="side">
        <div v-if="detail" class="box">
          <div class="box-h"><b>关于作者</b><span>AUTHOR</span></div>
          <div class="bd">
            <div class="author">
              <img class="ava" :src="user.avatarByName(detail.author, 48)" :alt="detail.author">
              <div><b>{{ detail.author }}</b><div class="gray2">{{ detail.authorGroup }}</div></div>
            </div>
            <div class="astat">
              <div><b>{{ detail.authorStats.postCount }}</b>发帖</div>
              <div><b>{{ detail.authorStats.likeTotal }}</b>获赞</div>
              <div><b>{{ detail.authorStats.collectedCount }}</b>被收藏</div>
            </div>
          </div>
        </div>
        <div class="box">
          <div class="box-h"><b>热议主题</b><span>HOT</span></div>
          <div class="bd">
            <ul class="hot">
              <li v-for="(h, i) in boardHot" :key="h.id">
                <span class="rk">{{ String(i + 1).padStart(2, '0') }}</span>
                <router-link :to="`/topic/${h.id}`">{{ h.title }}</router-link>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>

    <!-- 管理档统一弹窗：主题/楼层编辑 + 状态操作 -->
    <AdminDialog v-model="adminOpen" :target="adminTarget" @done="onAdminDone" />
  </div>
</template>

<style scoped>
/* 页面差异：双列网格 + 主楼结构；楼层/回复框/弹窗皮肤分别在各自组件与 skin.css */
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 22px; align-items: start; }
.post, .replies { margin-bottom: 22px; }
.post .in { padding: 20px 22px 16px; }
.p-top { display: flex; gap: 16px; }
.pava { width: 73px; height: 73px; flex: none; border: var(--vio2-bd-acc); box-shadow: var(--vio2-hard-xs); object-fit: cover; }
.ph-main { flex: 1; min-width: 0; }
.post h1 { font-size: 22px; font-weight: 900; line-height: 1.35; }
.p-meta { margin-top: 9px; font-size: 12px; color: var(--vio2-ink-3); font-family: var(--vio2-mono); }
.p-meta b { color: var(--vio2-blue); }
.p-body { margin-top: 18px; font-size: 14px; line-height: 1.8; font-weight: 600; }
.p-body p { margin-bottom: 10px; }
.ops { margin-top: 16px; display: flex; gap: 10px; }
.h-link { color: #C9C4B4; }
.h-link:hover { color: var(--vio2-yellow); }
.author { display: flex; gap: 11px; align-items: center; margin-bottom: 12px; }
.author b { font-size: 14px; }
.gray2 { font-size: 11px; color: var(--vio2-ink-3); font-family: var(--vio2-mono); margin-top:  3px; }
</style>
