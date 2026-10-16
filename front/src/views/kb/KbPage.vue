<script setup lang="ts">
/**
 * 知识库页（/kb，三端，KB01–KB08 暂定接口）
 * lzh 2026-10-14 拍板先行简化：条目 = 外部链接或上传文件二选一，分类与正文暂不做
 * PRD §3.14：浏览/收藏/评论全角色；发布/编辑/置顶/删除仅管理员
 */
import { onMounted, computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateKbItem, apiKbItems } from '@/api/kb'
import type { KbItem, KbSourceType } from '@/api/kb'
import { KB_SOURCE_LABEL } from '@/config/labels'
import { useUserStore } from '@/stores/user'
import UploadField from '@/components/UploadField.vue'
import ScrollLoader from '@/components/ScrollLoader.vue'
import KbDetailDrawer from './KbDetailDrawer.vue'

const user = useUserStore()
const isAdmin = user.role !== 'STUDENT'

const all = ref<KbItem[]>([])
/** 本地滚动分页：后端 KB01 返回全量数组，前端切片显示（每批 20 条） */
const loadedCount = ref(20)
const visible = computed(() => all.value.slice(0, loadedCount.value))
const noMore = computed(() => loadedCount.value >= all.value.length)
const keyword = ref('')
const loading = ref(false)

const drawerVisible = ref(false)
const detailId = ref('')

async function load() {
  loading.value = true
  try {
    all.value = await apiKbItems({ keyword: keyword.value || undefined })
    loadedCount.value = 20
  } finally {
    loading.value = false
  }
}

/** 滚动到底扩大切片（ScrollLoader 的 reach 回调） */
function loadMore() {
  if (loading.value || noMore.value) return
  loadedCount.value += 20
}

function openDetail(id: string) {
  detailId.value = id
  drawerVisible.value = true
}

// ── 发布（管理员）：链接 / 文件 二选一 ──
const formVisible = ref(false)
/** 弹窗重开自增，用于强制重挂 UploadField 清空其内部已选状态 */
const formSeq = ref(0)
const form = ref({ title: '', summary: '', sourceType: 'LINK' as KbSourceType, linkUrl: '' })
/** 文件形态：UploadField 上传成功后暂存文件服务返回值，发布时随条目提交 */
const uploaded = ref<{ fileId: string; fileName: string } | null>(null)
const submitting = ref(false)

function openCreate() {
  formSeq.value++
  form.value = { title: '', summary: '', sourceType: 'LINK', linkUrl: '' }
  uploaded.value = null
  formVisible.value = true
}

function onUploaded(f: { fileId: string; originalName: string }) {
  uploaded.value = { fileId: f.fileId, fileName: f.originalName }
}

async function submit() {
  if (submitting.value) return
  const title = form.value.title.trim()
  if (!title) {
    ElMessage.warning('标题不能为空')
    return
  }
  // 按形态校验：链接必填地址；文件必须先上传拿到 fileId
  if (form.value.sourceType === 'LINK' && !form.value.linkUrl.trim()) {
    ElMessage.warning('请填写外部链接地址')
    return
  }
  if (form.value.sourceType === 'FILE' && !uploaded.value) {
    ElMessage.warning('请先上传文件')
    return
  }
  submitting.value = true
  try {
    await apiCreateKbItem({
      title,
      summary: form.value.summary.trim(),
      sourceType: form.value.sourceType,
      linkUrl: form.value.sourceType === 'LINK' ? form.value.linkUrl.trim() : undefined,
      fileId: uploaded.value?.fileId,
      fileName: uploaded.value?.fileName
    })
    ElMessage.success('已发布')
    formVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="dossier-head">
      <h3>知识库</h3>
      <div class="dossier-meta mono">
        <span>教程 · 规范 · 资料沉淀区</span>
      </div>
    </div>

    <div class="fbar">
      <input v-model="keyword" class="input-line" style="width: 200px" placeholder="搜索标题 / 摘要" @keyup.enter="load" />
      <button class="btn-line" @click="load">查询</button>
      <span style="flex: 1"></span>
      <button v-if="isAdmin" class="btn-ink" @click="openCreate">发布内容</button>
    </div>

    <div class="kb-grid">
      <div v-for="it in visible" :key="it.id" class="kb-card" @click="openDetail(it.id)">
        <div class="kb-top">
          <span class="kb-cat">{{ KB_SOURCE_LABEL[it.sourceType] }}</span>
          <span v-if="it.pinned" class="kb-pin mono">置顶</span>
        </div>
        <h4>{{ it.title }}</h4>
        <p>{{ it.summary }}</p>
        <p v-if="it.sourceType === 'FILE'" class="kb-file mono">{{ it.fileName }}</p>
        <div class="kb-meta mono">
          <span>{{ it.publisherName }}</span>
          <span>阅 {{ it.viewCount }} · 藏 {{ it.favoriteCount }} · 评 {{ it.commentCount }}</span>
        </div>
      </div>
    </div>
    <p v-if="!loading && all.length === 0" class="empty-note">暂无内容</p>
    <ScrollLoader :loading="loading" :no-more="noMore" :loaded="visible.length" :total="all.length" @reach="loadMore" />

    <el-dialog v-model="formVisible" title="发布知识库内容" width="520px">
      <div class="form-col">
        <label>标题</label>
        <input v-model="form.title" class="input-line" />
        <label>摘要（选填，列表卡片展示）</label>
        <textarea v-model="form.summary" class="input-line" rows="2"></textarea>
        <label>内容形态（二选一）</label>
        <div class="type-row">
          <button type="button" class="type-btn" :class="{ active: form.sourceType === 'LINK' }" @click="form.sourceType = 'LINK'">外部链接</button>
          <button type="button" class="type-btn" :class="{ active: form.sourceType === 'FILE' }" @click="form.sourceType = 'FILE'">上传文件</button>
        </div>
        <template v-if="form.sourceType === 'LINK'">
          <label>链接地址</label>
          <input v-model="form.linkUrl" class="input-line" placeholder="https://…" />
        </template>
        <template v-else>
          <label>文件（选择后立即上传并安全扫描）</label>
          <UploadField
            :key="formSeq"
            biz-type="KB_FILE"
            accept=".pdf,.mp4,.mov,.avi,.mkv,.webm,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.zip,.png,.jpg"
            tip="支持 PDF / 视频（mp4/mov/avi/mkv/webm）/ Office 文档 / 压缩包 / 图片，单文件上限 200MB"
            :max-mb="200"
            button-text="选择文件上传"
            @uploaded="onUploaded"
          />
        </template>
      </div>
      <template #footer>
        <button class="btn-line" @click="formVisible = false">取消</button>
        <button class="btn-ink" :disabled="submitting" @click="submit">发布</button>
      </template>
    </el-dialog>

    <KbDetailDrawer v-model="drawerVisible" :kb-id="detailId" @changed="load" />
  </div>
</template>

<style scoped>
.kb-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.kb-card { background: var(--vio-sheet); border: 1px solid var(--vio-rule); padding: 16px 18px; cursor: pointer; transition: border-color 0.2s var(--vio-ease); }
.kb-card:hover { border-color: var(--vio-blue-ink); }
.kb-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.kb-cat { font-size: 11px; color: var(--vio-blue-ink); border: 1px solid var(--vio-blue-ink); padding: 1px 8px; }
.kb-pin { font-size: 10px; color: var(--vio-seal); border: 1px solid var(--vio-seal); padding: 1px 6px; }
.kb-card h4 { font-size: 15px; color: var(--vio-ink); margin-bottom: 6px; font-weight: 700; }
.kb-card p { font-size: 12.5px; color: var(--vio-ink-2); line-height: 1.7; margin-bottom: 10px; }
.kb-file { font-size: 11.5px; color: var(--vio-ink-3); margin: -4px 0 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--vio-ink-3); }
.form-col { display: flex; flex-direction: column; gap: 6px; }
.form-col label { font-size: 12px; color: var(--vio-ink-3); margin-top: 6px; }
.type-row { display: flex; gap: 8px; }
.type-btn {
  padding: 7px 20px; border: 1px solid var(--vio-rule); background: var(--vio-sheet);
  color: var(--vio-ink-2); font-size: 13px; cursor: pointer; transition: all 0.2s var(--vio-ease);
}
.type-btn.active { border-color: var(--vio-ink); color: var(--vio-ink); font-weight: 700; box-shadow: inset 0 -2px 0 var(--vio-ink); }
</style>
