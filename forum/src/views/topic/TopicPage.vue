<script setup lang="ts">
/**
 * 帖子详情：面包屑 + 主楼（左 73px 大头像）+ 回复楼层 + 回复框沉底 + 右栏
 * 视觉基准：.agentdocs/design-preview/forum-v2ex/topic.html（v3 样张，lzh 已过目）
 * 数据：F04 详情（浏览计数服务端 +1）、F05 楼层、F02 本版热议
 * 写操作：F07 回帖（幂等键防重）、F08/F10 点赞、F09 收藏（后三者为状态型天然幂等）
 * TODO(联调-论坛清单)：以上路径为前端预设；附件下载依赖文件服务方案，点击仅提示
 */
import { onMounted, ref } from 'vue'
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

async function loadFloors() {
  const res = await apiReplyList(topicId)
  floors.value = res.list
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

/** 主楼点赞/取消（状态型：重复点无副作用） */
async function toggleLike() {
  if (!detail.value) return
  const target = !detail.value.liked
  const res = await apiLikeTopic(detail.value.id, target)
  detail.value.liked = res.liked
  detail.value.likeCount += res.liked ? 1 : -1
}

/** 主楼收藏/取消 */
async function toggleCollect() {
  if (!detail.value) return
  const target = !detail.value.collected
  const res = await apiCollectTopic(detail.value.id, target)
  detail.value.collected = res.collected
}

/** 楼层点赞/取消 */
async function toggleFloorLike(f: ReplyItem) {
  const target = !f.liked
  const res = await apiLikeReply(f.id, target)
  f.liked = res.liked
  f.likeCount += res.liked ? 1 : -1
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
  if (submitting.value) return
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
    await loadFloors()
    if (detail.value) detail.value.replyCount = floors.value.length
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
    <div v-if="detail" class="crumb"><router-link to="/">首页</router-link> › <a href="#" @click.prevent>{{ boardName(detail.board) }}</a> › 主题</div>
    <div class="grid">
      <div>
        <!-- 主楼：V2EX 结构 = 左 73px 大头像 + 标题/meta -->
        <div v-if="detail" class="box post">
          <div class="p-head">
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
            <button type="button" :class="{ on: detail.liked }" @click="toggleLike">{{ detail.liked ? '👍 已点赞' : '👍 点赞' }} {{ detail.likeCount }}</button>
            <button type="button" :class="{ on: detail.collected }" @click="toggleCollect">{{ detail.collected ? '⭐ 已收藏' : '⭐ 收藏' }}</button>
            <button type="button" @click="scrollToReply">回复</button>
          </div>
        </div>

        <!-- 回复盒：灰底头行 + 楼层 -->
        <div class="box replies">
          <div class="r-head">{{ floors.length }} 条回复 · 按时间正序 <a href="#reply">添加新回复 ↓</a></div>
          <div v-for="f in floors" :key="f.id" class="floor">
            <img class="fava" :src="avatarOf(f.author, 48)" :alt="f.author">
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
        </div>

        <!-- 回复框沉底 -->
        <div class="box reply" id="reply">
          <h5>添加一条新回复</h5>
          <div v-if="quoteReply" class="q-tip">
            引用 @{{ quoteReply.author }} #{{ quoteReply.floorNo }}
            <a href="#" @click.prevent="quoteReply = null">取消</a>
          </div>
          <textarea ref="replyBox" v-model="replyText" :disabled="submitting" placeholder="支持 Markdown；回复即留痕，删除需管理员操作"></textarea>
          <div class="r-foot">
            <i>※ 文明发言，排障类回复请尽量附现象、配置与波形</i>
            <button class="btn-post" type="button" :disabled="submitting" @click="submitReply">
              {{ submitting ? '回复中…' : '回复' }}
            </button>
          </div>
        </div>
      </div>

      <div class="side">
        <div v-if="detail" class="box">
          <h5>关于作者</h5>
          <div class="author">
            <img :src="avatarOf(detail.author, 48)" :alt="detail.author">
            <div><b>{{ detail.author }}</b><div class="gray">{{ detail.authorGroup }}</div></div>
          </div>
          <div class="stat">
            <div><b>{{ detail.authorStats.postCount }}</b>发帖</div>
            <div><b>{{ detail.authorStats.likeTotal }}</b>获赞</div>
            <div><b>{{ detail.authorStats.archivedCount }}</b>被归档</div>
          </div>
        </div>
        <div class="box">
          <h5>热议主题</h5>
          <ul class="hot">
            <li v-for="h in boardHot" :key="h.id">
              <img class="hava" :src="avatarOf(h.author, 24)" :alt="h.author">
              <router-link :to="`/topic/${h.id}`">{{ h.title }}</router-link>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: 1fr 270px; gap: 20px; align-items: start; }
.box { background: var(--box); border-radius: var(--radius-box); box-shadow: var(--shadow-box); }
.gray { font-size: 12px; color: var(--fade); }
.crumb { font-size: 12.5px; color: var(--fade); margin-bottom: 10px; }
.crumb a:hover { color: var(--ink); }

/* 主楼 */
.post { padding: 18px 20px 14px; margin-bottom: 18px; }
.p-head { display: flex; gap: 14px; }
.pava { width: 73px; height: 73px; flex: none; border-radius: var(--radius-ava); object-fit: cover; }
.ph-main { flex: 1; min-width: 0; }
.post h1 { font-size: 21px; color: var(--ink); line-height: 1.3; font-weight: 500; }
.pin { font-size: 11px; color: var(--fade); border: 1px solid #DDD; border-radius: 3px; padding: 0 5px; margin-right: 6px; vertical-align: 2px; font-weight: 400; }
.p-meta { margin-top: 8px; font-size: 12px; color: var(--fade); }
.node { background: #F5F5F5; color: var(--fade); border-radius: 10px; padding: 1px 8px; }
.p-meta b { color: var(--meta); }
.p-body { margin-top: 16px; font-size: 14px; line-height: 1.7; color: var(--ink); }
.p-body p { margin-bottom: 10px; }
.attach { margin-top: 14px; background: var(--box-soft); border-radius: var(--radius-box); padding: 10px 14px; font-size: 12.5px; }
.attach div { display: flex; justify-content: space-between; padding: 3px 0; }
.attach a:hover { color: var(--ink); }
.ops { margin-top: 16px; display: flex; gap: 8px; }
.ops button { background: #F5F5F5; border: 1px solid var(--line); color: var(--body); font-size: 12.5px; padding: 5px 14px; border-radius: var(--radius-box); cursor: pointer; }
.ops button:hover { background: #EBEBEB; }
.ops button.on { background: var(--pill); border-color: var(--pill); color: #FFF; }

/* 楼层 */
.replies { margin-bottom: 18px; }
.r-head { padding: 9px 14px; border-bottom: 1px solid var(--line); font-size: 12.5px; color: var(--fade); background: var(--box-soft); border-radius: var(--radius-box) var(--radius-box) 0 0; display: flex; }
.r-head a { margin-left: auto; }
.r-head a:hover { color: var(--ink); }
.floor { display: flex; gap: 12px; padding: 13px 14px; border-bottom: 1px solid var(--line-soft); }
.floor:last-child { border-bottom: none; }
.fava { width: 48px; height: 48px; flex: none; border-radius: var(--radius-ava); object-fit: cover; }
.f-main { flex: 1; min-width: 0; }
.f-meta { font-size: 12px; color: var(--fade); margin-bottom: 4px; display: flex; align-items: baseline; }
.f-meta b { color: var(--meta); margin-right: 8px; font-size: 13px; }
.fno { margin-left: auto; color: #D0D0D0; font-size: 12px; }
.quote { background: var(--box-soft); border-left: 2px solid var(--line); padding: 6px 10px; font-size: 12.5px; color: var(--fade); border-radius: 2px; margin-bottom: 6px; }
.f-text { font-size: 14px; color: var(--ink); line-height: 1.7; }
.f-ops { margin-top: 5px; font-size: 12px; }
.f-ops a { margin-right: 12px; color: #B0B0B0; }
.f-ops a:hover, .f-ops a.on { color: var(--pill); }

/* 回复框 */
.reply { padding: 14px 18px 16px; }
.reply h5 { font-size: 13px; color: var(--body); margin-bottom: 10px; font-weight: 600; }
.q-tip { font-size: 12px; color: var(--meta); background: var(--box-soft); border-left: 2px solid var(--pill); border-radius: 2px; padding: 5px 10px; margin-bottom: 8px; }
.q-tip a { color: var(--fade); margin-left: 8px; }
.reply textarea { width: 100%; height: 110px; border: 1px solid var(--line); border-radius: var(--radius-box); padding: 10px; font-size: 13px; line-height: 1.7; font-family: inherit; resize: vertical; background: var(--box-soft); color: var(--ink); box-sizing: border-box; }
.reply textarea:focus { outline: none; border-color: #CCC; background: var(--box); }
.r-foot { display: flex; align-items: center; margin-top: 10px; }
.r-foot i { font-style: normal; font-size: 12px; color: var(--fade); flex: 1; }
.btn-post { font-size: 13px; color: var(--ink); background: var(--box); border: 1px solid #CCC; border-radius: var(--radius-box); padding: 5px 14px; cursor: pointer; }
.btn-post:hover:not(:disabled) { border-color: #999; }
.btn-post:disabled { opacity: 0.55; cursor: not-allowed; }

/* 右栏 */
.side .box { padding: 12px 14px; margin-bottom: 18px; }
.side h5 { font-size: 12.5px; color: var(--fade); font-weight: 400; border-bottom: 1px solid var(--line-soft); padding-bottom: 8px; margin-bottom: 8px; }
.author { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.author img { width: 48px; height: 48px; border-radius: var(--radius-ava); object-fit: cover; }
.author b { color: var(--ink); font-size: 14px; }
.stat { display: flex; font-size: 12px; color: var(--fade); }
.stat div { flex: 1; text-align: center; }
.stat b { display: block; font-size: 16px; color: var(--body); font-weight: 600; }
.hot { margin: 0; padding: 0; }
.hot li { list-style: none; display: flex; gap: 9px; align-items: flex-start; padding: 7px 0; border-bottom: 1px solid #F4F4F4; }
.hot li:last-child { border: none; }
.hava { width: 24px; height: 24px; flex: none; border-radius: var(--radius-ava); object-fit: cover; margin-top: 1px; }
.hot a { font-size: 12.5px; color: var(--body); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.hot a:hover { color: #000; }
</style>
