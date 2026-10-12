<script setup lang="ts">
/**
 * 项目文件面板（PR25–PR28）
 * 上传：先走骨架 F01 拿 fileId（UploadField），再 PR26 建关联（幂等）；所有人与项目成员可传
 * 删除：所有人可删任意，成员仅可删自己上传的（7002 后端拦）
 * 下载走 PR28（业务线先校验可见性再转发，不暴露对象存储地址；mock 回提示）
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAttachFile, apiDeleteProjectFile, apiDownloadProjectFile, apiProjectFiles } from '@/api/project'
import type { ProjectFile } from '@/api/project'
import { PROJECT_FILE_CATEGORY_LABEL } from '@/config/labels'
import UploadField from '@/components/UploadField.vue'
import type { UploadResp } from '@/api/file'

const props = defineProps<{ projectId: string; canEdit: boolean }>()

const list = ref<ProjectFile[]>([])
const category = ref('DOC')

async function load() {
  list.value = await apiProjectFiles(props.projectId)
}

async function onUploaded(f: UploadResp) {
  await apiAttachFile(props.projectId, { fileId: f.fileId, fileName: f.fileName, fileCategory: category.value })
  ElMessage.success('文件已关联到项目')
  await load()
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
  const r = await apiDownloadProjectFile(f.id)
  ElMessage.success(r.note)
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
      <UploadField accept=".pdf,.docx,.zip,.jpg,.png" :max-mb="20" button-text="上传文件" @uploaded="onUploaded" />
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
