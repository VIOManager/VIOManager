<script setup lang="ts">
/**
 * 知识库详情抽屉（KB03–KB08）
 * lzh 2026-10-14 拍板简化：条目 = 外部链接或上传文件二选一
 * 链接条目给「打开链接」；文件条目给「下载文件」（走文件服务流下载）
 * 收藏切换 + 评论区（本人/管理员可删）+ 管理员操作（编辑/置顶/删除）
 * ⚠️ 编辑暂只改标题/摘要/链接；换文件请删除后重新发布（简化期不整细）
 */
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAddKbComment, apiDeleteKbComment, apiDeleteKbItem, apiDownloadKbFile, apiKbComments, apiKbDetail, apiPinKbItem, apiToggleKbFavorite, apiUpdateKbItem } from '@/api/kb'
import type { KbComment, KbDetail } from '@/api/kb'
import { KB_SOURCE_LABEL } from '@/config/labels'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ modelValue: boolean; kbId: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; changed: [] }>()

const user = useUserStore()
const isAdmin = user.role !== 'STUDENT'

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => {
  visible.value = v
  if (v) void load()
})
watch(visible, (v) => emit('update:modelValue', v))

const detail = ref<KbDetail | null>(null)
const comments = ref<KbComment[]>([])
const commentText = ref('')
const sending = ref(false)

async function load() {
  if (!props.kbId) return
  const [d, c] = await Promise.all([apiKbDetail(props.kbId), apiKbComments(props.kbId)])
  detail.value = d
  comments.value = c
}

async function onFavorite() {
  if (!detail.value) return
  // 本地联调改动：后端收藏是 collect/uncollect 两端点，需传当前态（待 lzh 复核）
  const r = await apiToggleKbFavorite(detail.value.id, detail.value.favorited)
  detail.value.favorited = r.favorited
  detail.value.favoriteCount += r.favorited ? 1 : -1
  emit('changed')
}

/** 链接条目：新标签页打开（noopener 防钓鱼页反手改原页） */
function onOpenLink() {
  if (!detail.value?.linkUrl) return
  window.open(detail.value.linkUrl, '_blank', 'noopener')
}

/** 文件条目：走文件服务流下载，文件名用条目存的原始名兜底 */
async function onDownload() {
  if (!detail.value?.fileId) return
  await apiDownloadKbFile(detail.value.fileId, detail.value.fileName)
}

async function onSendComment() {
  if (!detail.value || sending.value) return
  if (!commentText.value.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  sending.value = true
  try {
    await apiAddKbComment(detail.value.id, commentText.value.trim())
    commentText.value = ''
    await load()
    emit('changed')
  } finally {
    sending.value = false
  }
}

async function onDeleteComment(c: KbComment) {
  try {
    await ElMessageBox.confirm('删除该评论？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteKbComment(c.id)
  await load()
  emit('changed')
}

// ── 管理员操作 ──
const editVisible = ref(false)
const editForm = ref({ title: '', summary: '', linkUrl: '' })

function openEdit() {
  if (!detail.value) return
  editForm.value = { title: detail.value.title, summary: detail.value.summary, linkUrl: detail.value.linkUrl }
  editVisible.value = true
}

async function submitEdit() {
  if (!detail.value) return
  await apiUpdateKbItem(detail.value.id, editForm.value)
  ElMessage.success('已保存')
  editVisible.value = false
  await load()
  emit('changed')
}

async function onPin() {
  if (!detail.value) return
  await apiPinKbItem(detail.value.id, !detail.value.pinned)
  ElMessage.success(detail.value.pinned ? '已取消置顶' : '已置顶')
  await load()
  emit('changed')
}

async function onDelete() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('删除「' + detail.value.title + '」？评论与收藏一并清除', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteKbItem(detail.value.id)
  ElMessage.success('已删除')
  visible.value = false
  emit('changed')
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? detail.title : '内容详情'" size="560px">
    <template v-if="detail">
      <div class="dossier-meta mono" style="margin-bottom: 12px">
        <span class="stamp ok">{{ KB_SOURCE_LABEL[detail.sourceType] }}</span>
        <span v-if="detail.pinned" style="color: var(--vio-seal)">置顶</span>
      </div>
      <dl class="sheet">
        <div><dt>发布人</dt><dd>{{ detail.publisherName }}</dd></div>
        <div><dt>发布时间</dt><dd class="mono">{{ detail.createdAt }}</dd></div>
        <div v-if="detail.sourceType === 'FILE'"><dt>文件</dt><dd class="mono" style="font-size: 12px">{{ detail.fileName }}</dd></div>
      </dl>
      <p v-if="detail.summary" class="summary">{{ detail.summary }}</p>

      <div class="zone action-row">
        <button v-if="detail.sourceType === 'LINK'" class="btn-ink" @click="onOpenLink">打开链接</button>
        <button v-if="detail.sourceType === 'FILE'" class="btn-ink" @click="onDownload">下载文件</button>
        <button class="btn-line" @click="onFavorite">{{ detail.favorited ? '★ 已收藏（点击取消）' : '☆ 收藏' }}</button>
        <template v-if="isAdmin">
          <button class="btn-line" @click="openEdit">编辑</button>
          <button class="btn-line" @click="onPin">{{ detail.pinned ? '取消置顶' : '置顶' }}</button>
          <button class="btn-line" @click="onDelete">删除</button>
        </template>
      </div>

      <div class="zone">
        <h6>评论（{{ comments.length }}）</h6>
        <div v-for="c in comments" :key="c.id" class="comment">
          <div class="comment-head">
            <b>{{ c.authorName }}</b>
            <span class="mono">{{ c.createdAt }}</span>
            <button v-if="c.mine" class="btn-act" @click="onDeleteComment(c)">删</button>
          </div>
          <p>{{ c.content }}</p>
        </div>
        <div class="comment-input">
          <input v-model="commentText" class="input-line" style="flex: 1" placeholder="写下你的评论…" @keyup.enter="onSendComment" />
          <button class="btn-ink" :disabled="sending" @click="onSendComment">发表</button>
        </div>
      </div>

      <el-dialog v-model="editVisible" title="编辑内容" width="520px" append-to-body>
        <div class="form-col">
          <label>标题</label>
          <input v-model="editForm.title" class="input-line" />
          <label>摘要</label>
          <textarea v-model="editForm.summary" class="input-line" rows="2"></textarea>
          <template v-if="detail.sourceType === 'LINK'">
            <label>链接地址</label>
            <input v-model="editForm.linkUrl" class="input-line" />
          </template>
          <p v-else class="fs" style="color: var(--vio-ink-3)">文件条目不支持换文件：如需更换请删除后重新发布</p>
        </div>
        <template #footer>
          <button class="btn-line" @click="editVisible = false">取消</button>
          <button class="btn-ink" @click="submitEdit">保存</button>
        </template>
      </el-dialog>
    </template>
  </el-drawer>
</template>

<style scoped>
.sheet div { display: flex; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.sheet dt { width: 76px; color: var(--vio-ink-3); flex-shrink: 0; }
.sheet dd { color: var(--vio-ink); }
.summary { margin: 14px 0 0; font-size: 13px; line-height: 1.8; color: var(--vio-ink-2); }
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.action-row { display: flex; gap: 8px; flex-wrap: wrap; }
.comment { padding: 8px 0; border-bottom: 1px dashed var(--vio-rule-soft); }
.comment-head { display: flex; align-items: center; gap: 10px; font-size: 12px; }
.comment-head b { color: var(--vio-ink); }
.comment-head span { color: var(--vio-ink-3); font-size: 11px; }
.comment p { margin: 4px 0 0; font-size: 13px; color: var(--vio-ink-2); }
.comment-input { display: flex; gap: 8px; margin-top: 12px; }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
</style>
