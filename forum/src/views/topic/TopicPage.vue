<script setup lang="ts">
/**
 * 帖子详情：mono 面包屑 + 主楼（左 73px 方头像）+ 回复楼层 + 回复框沉底 + 右栏
 * 视觉基准：.agentdocs/design-preview/2026-10-15-forum-redesign/topic.html（新粗野双主题，lzh 已拍板）
 * 数据：F04 详情（浏览计数服务端 +1）、F05 楼层（一级分页）、F02 本版热议
 * 写操作：F07 回帖（幂等键防重）、F08/F10 点赞、F09 收藏（后三者为状态型天然幂等）
 * 契约基准：backend/vio-forum 控制器（F01-F10 已实现）；LOCKED 主题禁回（后端 9101 兜底，前端先拦）
 * 附件下载依赖文件服务方案，点击仅提示
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { avatarOf } from '@/config/avatars'
import { boardName } from '@/config/boards'
import {
  apiTopicDetail, apiReplyList, apiCreateReply, apiLikeTopic, apiCollectTopic, apiLikeReply,
  type TopicDetail, type ReplyItem, type HotTopicItem, apiHotTopics
} from '@/api/forum'

const route = useRoute()
const topicId = String(route.params.id)

const detail = ref<TopicDetail | null>(null)
const floors = ref<ReplyItem[]>([])
const boardHot = ref<HotTopicItem[]>([])

/** 楼层分页（后端 F05 一级分页、size 上限 100） */
const REPLY_PAGE_SIZE = 20
const replyPage = ref(1)
const replyTotal = ref(0)

/** LOCKED 主题禁回（后端状态字段透出；管理员 F12 状态接口落地后联动） */
const locked = computed(() => detail.value?.status === 'LOCKED')

/** 回复框状态：正文、引用楼层、提交中标志 */
const replyText = ref('')
const quoteReply = ref<ReplyItem | null>(null)
const submitting = ref(false)
const replyBox = ref<HTMLTextAreaElement | null>(null)

/**
 * ⚠️ 回帖幂等键：进入页面生成；提交失败保留（重试同键，防断网重复回帖）；
 * 提交成功后换新键（接着回复下一楼是新的内容）
 */
const formKey = ref(crypto.randomUUID())

async function loadDetail() {
  try {
    detail.value = await apiTopicDetail(topicId)
  } catch {
    // 帖子不存在等业务错误：拦截器已提示，页面停留空态
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

/** 操作锁：点赞/收藏是天然幂等操作，锁只为防连点白发一堆请求 */
const likePending = ref(false)
const collectPending = ref(false)
const floorLikeLocks = ref<Record<string, boolean>>({})

/** 主楼点赞/取消（状态型：重复点无副作用；计数以服务端返回为准） */
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

/** 主楼收藏/取消 */
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

/** 楼层点赞/取消（按楼层粒度加锁） */
async function toggleFloorLike(f: ReplyItem) {
  if (floorLikeLocks.value[f.id]) return
  floorLikeLocks.value[f.id] = true
  try {
    const target = !f.liked
    const res = await apiLikeReply(f.id, target)
    f.liked = res.liked
    f.likeCount += res.liked ? 1 : -1
  } finally {
    floorLikeLocks.value[f.id] = false
  }
}

/** 楼层"回复"：带上引用并聚焦回复框 */
function quoteFloor(f: ReplyItem) {
  quoteReply.value = f
  replyText.value = ''
  if (replyBox.value) replyBox.value.focus()
}

/** 主楼"回复"按钮：滚动到沉底回复框并聚焦 */
function scrollToReply() {
  if (replyBox.value) {
    replyBox.value.scrollIntoView({ behavior: 'smooth', block: 'center' })
    replyBox.value.focus()
  }
}

/** 提交回复（幂等 + 双层防重：submitting 置灰拦手抖，formKey 拦网络重试重复） */
async function submitReply() {
  if (submitting.value || locked.value) return
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    await apiCreateReply(
      topicId,
      { text: replyText.value.trim(), quoteReplyId: quoteReply.value ? quoteReply.value.id : null },
      formKey.value
    )
    ElMessage.success('回复成功')
    replyText.value = ''
    quoteReply.value = null
    formKey.value = crypto.randomUUID()
    // 新楼在末尾：跳到最后一页看自己的回复；replyCount 以重查的 total 为准
    replyPage.value = Math.max(1, Math.ceil((replyTotal.value + 1) / REPLY_PAGE_SIZE))
    await loadFloors()
  } catch {
    // 失败：内容与 formKey 都保留，改完再点是同一次提交
    submitting.value = false
  }
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
        <!-- 主楼：左 73px 方头像 + 标题/meta -->
        <div v-if="detail" class="box post">
          <div class="in">
            <div class="p-top">
              <img class="pava" :src="avatarOf(detail.author, 73)" :alt="detail.author">
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
              <button type="button" class="chipbtn" @click="scrollToReply">↩ 回复</button>
            </div>
          </div>
        </div>

        <!-- 回复盒：黑条头行 + 楼层（楼层样式走 skin.css） -->
        <div class="box replies">
          <div class="box-h"><b>{{ replyTotal }} 条回复 · 按时间正序</b><span><a href="#reply" class="h-link">添加新回复 ↓</a></span></div>
          <div v-for="f in floors" :key="f.id" class="floor">
            <img class="ava" :src="avatarOf(f.author, 48)" :alt="f.author">
            <div class="f-main">
              <div class="f-meta"><b>{{ f.author }}</b>{{ f.authorGroup }} · {{ f.publishAt }}<span class="fno">#{{ f.floorNo }}</span></div>
              <div v-if="f.quote" class="quote">@{{ f.quote.author }} #{{ f.quote.floorNo }}：{{ f.quote.text }}</div>
              <div class="f-text">{{ f.text }}</div>
              <div class="f-ops">
                <a href="#" :class="{ on: f.liked }" @click.prevent="toggleFloorLike(f)">♥ {{ f.likeCount }}</a>
                <a href="#reply" @click.prevent="quoteFloor(f)">回复</a>
              </div>
            </div>
          </div>
          <div v-if="replyTotal > REPLY_PAGE_SIZE" class="pager">
            <a href="#" :class="{ off: replyPage <= 1 }" @click.prevent="turnReplyPage(-1)">‹</a>
            <span class="info">PAGE {{ replyPage }} / {{ Math.max(1, Math.ceil(replyTotal / REPLY_PAGE_SIZE)) }} · {{ replyTotal }} FLOORS</span>
            <a href="#" :class="{ off: replyPage * REPLY_PAGE_SIZE >= replyTotal }" @click.prevent="turnReplyPage(1)">›</a>
          </div>
        </div>

        <!-- 回复框沉底（LOCKED 主题禁回：后端 9101 兜底，前端先行拦截） -->
        <div class="box replybox" id="reply">
          <div class="in">
            <h5>添加一条新回复</h5>
            <div v-if="locked" class="q-tip">🔒 主题已被管理员锁定，暂停回复</div>
            <div v-else-if="quoteReply" class="q-tip">
              引用 @{{ quoteReply.author }} #{{ quoteReply.floorNo }}
              <a href="#" @click.prevent="quoteReply = null">取消</a>
            </div>
            <textarea ref="replyBox" v-model="replyText" :disabled="submitting || locked" placeholder="回复即留痕，删除需管理员操作"></textarea>
            <div class="r-foot">
              <i>※ 文明发言，排障类回复请尽量附现象、配置与波形</i>
              <button class="btn" type="button" :disabled="submitting || locked" @click="submitReply">
                {{ submitting ? '回复中…' : '回复' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="side">
        <div v-if="detail" class="box">
          <div class="box-h"><b>关于作者</b><span>AUTHOR</span></div>
          <div class="bd">
            <div class="author">
              <img class="ava" :src="avatarOf(detail.author, 48)" :alt="detail.author">
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
  </div>
</template>

<style scoped>
/* 页面差异：双列网格 + 主楼/回复框结构；楼层/附件/统计格走 skin.css 共享组件 */
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
.replybox .in { padding: 16px 18px 18px; }
.replybox h5 { font-size: 13px; font-weight: 900; letter-spacing: 0.1em; margin-bottom: 10px; }
.q-tip { font-size: 12px; color: var(--vio2-ink-2); background: var(--vio2-panel-2); border-left: 4px solid var(--vio2-lilac); padding: 5px 10px; margin-bottom: 8px; font-weight: 600; }
.q-tip a { color: var(--vio2-ink-3); margin-left: 8px; }
.replybox textarea { width: 100%; height: 110px; border: var(--vio2-bd-main); background: var(--vio2-panel-2); color: var(--vio2-ink); padding: 11px; font: inherit; font-size: 13px; line-height: 1.7; resize: vertical; }
.replybox textarea:focus { outline: none; border-color: var(--vio2-blue); }
.r-foot { display: flex; align-items: center; margin-top: 12px; gap: 14px; }
.r-foot i { font-style: normal; font-size: 11.5px; color: var(--vio2-ink-3); flex: 1; font-family: var(--vio2-mono); }
.author { display: flex; gap: 11px; align-items: center; margin-bottom: 12px; }
.author b { font-size: 14px; }
.gray2 { font-size: 11px; color: var(--vio2-ink-3); font-family: var(--vio2-mono); margin-top: 3px; }
</style>
