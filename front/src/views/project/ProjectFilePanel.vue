<script setup lang="ts">
/**
 * 项目文件面板（PR25–PR28）
 * 上传：单段 multipart（file + fileCategory + remark），骨架 F01 校验与 ClamAV 检测在后端一体完成；
 *      所有人与项目成员可传（后端 7002 拦）
 * 删除：所有人可删任意，成员仅可删自己上传的（7002 后端拦；逻辑删关联，文件本体保留）
 * 下载：blob 流式保存（PR28 先过项目可见性再转发，不暴露对象存储地址）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiDeleteProjectFile, apiDownloadProjectFile, apiProjectFiles, apiUploadProjectFile } from '@/api/project'
import type { ProjectFile } from '@/api/project'
import { PROJECT_FILE_CATEGORY_LABEL } from '@/config/labels'

const props = defineProps<{ projectId: string; canEdit: boolean }>()

const list = ref<ProjectFile[]>([])
const category = ref('DOC')
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

async function load() {
  list.value = await apiProjectFiles(props.projectId)
}

async function onPickFile() {
  const input = fileInput.value
  if (!input || !input.files || input.files.length === 0) return
  const file = input.files[0]
  uploading.value = true
  try {
    await apiUploadProjectFile(props.projectId, file, category.value)
    ElMessage.success('文件已上传并关联到项目')
    await load()
  } finally {
    uploading.value = false
    // 重置 input 让同一文件可再次选择
    input.value = ''
  }
}

async function onDelete(f: ProjectFile) {
  try {
    await ElMessageBox.confirm('删除文件「' + f.fileName + '」？（逻辑删关联，文件本体保留）', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await apiDeleteProjectFile(props.projectId, f.id)
  await load()
}

async function onDownload(f: ProjectFile) {
  await apiDownloadProjectFile(f.id, f.fileName)
}

function sizeText(n: number): string {
  if (!n) return '—'
  if (n >= 1048576) return (n / 1048576).toFixed(1) + ' MB'
  return Math.round(n / 1024) + ' KB'
}

onMounted(load)
</script>

<template>
  <div class="zone">
    <h6>项目文件（病毒检测强制，INFECTED 拒绝）</h6>
    <div class="up-row">
      <select v-model="category" class="input-line" style="width: 100px">
        <option v-for="(label, v) in PROJECT_FILE_CATEGORY_LABEL" :key="v" :value="v">{{ label }}</option>
      </select>
      <input ref="fileInput" type="file" accept=".pdf,.docx,.zip,.jpg,.png" style="display: none" @change="onPickFile" />
      <button class="btn-act" :disabled="uploading" @click="fileInput?.click()">{{ uploading ? '上传中…' : '上传文件' }}</button>
    </div>
    <div v-for="f in list" :key="f.id" class="row-line">
      <span>{{ f.fileName }}<em class="mono">{{ PROJECT_FILE_CATEGORY_LABEL[f.fileCategory] }} · {{ sizeText(f.fileSize) }} · {{ f.uploadedByName }}</em></span>
      <span>
        <button class="btn-act" @click="onDownload(f)">下载</button>
        <button class="btn-act" @click="onDelete(f)">删除</button>
      </span>
    </div>
    <p v-if="list.length === 0" class="dim-note">暂无文件</p>
  </div>
</template>

<style scoped>
.zone { margin-top: 18px; padding-top: 12px; border-top: 1px solid var(--vio-rule-soft); }
.zone h6 { font-size: 12px; color: var(--vio-ink-3); margin-bottom: 10px; letter-spacing: 0.08em; }
.up-row { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
.row-line { display: flex; align-items: center; justify-content: space-between; padding: 7px 0; border-bottom: 1px dashed var(--vio-rule-soft); font-size: 13px; }
.row-line em { font-style: normal; font-size: 11px; color: var(--vio-ink-3); margin-left: 8px; }
.dim-note { font-size: 12px; color: var(--vio-ink-3); }
</style>
