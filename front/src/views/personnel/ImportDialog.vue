<script setup lang="ts">
/**
 * Excel 批量导入弹窗（A 线 M06 模板下载 + M07 导入 + 行级错误回显）
 * 流程：选文件 → 带幂等键上传 → 展示成功/失败数与行号原因（学号重复行跳过）
 * ⚠️ 幂等：一次选择生成一个幂等键，重复点导入不重复入库（规范 §6）
 */
import { ref } from 'vue'
import { apiDownloadTemplate, apiImportMembers } from '@/api/member'
import type { ImportResult } from '@/api/member'

const visible = defineModel<boolean>({ required: true })
const emit = defineEmits<{ done: [] }>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const pickedName = ref('')
const result = ref<ImportResult | null>(null)
let currentFile: File | null = null
let idemKey = ''

function pick() {
  if (!uploading.value) inputRef.value?.click()
}

function onChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files && input.files[0]
  input.value = ''
  if (!file) return
  currentFile = file
  pickedName.value = file.name
  idemKey = crypto.randomUUID()
  result.value = null
  void doImport()
}

async function doImport() {
  if (!currentFile || uploading.value) return
  uploading.value = true
  try {
    result.value = await apiImportMembers(currentFile, idemKey)
    if (result.value.successCount > 0) emit('done')
  } catch {
    // 幂等缺失/格式错误等提示已由 http 拦截器弹出
  } finally {
    uploading.value = false
  }
}

function onClose() {
  visible.value = false
  currentFile = null
  pickedName.value = ''
  result.value = null
}
</script>

<template>
  <el-dialog v-model="visible" title="Excel 批量导入" width="540px" @close="onClose">
    <div class="row">
      <button class="btn-line" @click="apiDownloadTemplate">下载导入模板</button>
      <span class="fs tip">模板 14 列（姓名/类型必填）；证件照、签名列留空，导入后到详情页上传</span>
    </div>
    <div class="row">
      <input ref="inputRef" type="file" accept=".xlsx,.xls,.csv" class="hidden" @change="onChange" />
      <button class="btn-ink" :disabled="uploading" @click="pick">{{ uploading ? '导入中…' : '选择文件导入' }}</button>
      <span v-if="pickedName" class="mono fname">{{ pickedName }}</span>
    </div>
    <div v-if="result" class="result">
      <p class="sum">
        成功 <b class="mono ok">{{ result.successCount }}</b> 行，失败 <b class="mono bad">{{ result.failCount }}</b> 行
      </p>
      <table v-if="result.errors.length > 0" class="ledger">
        <thead><tr><th style="width: 70px">行号</th><th>失败原因</th></tr></thead>
        <tbody>
          <tr v-for="e in result.errors" :key="e.row">
            <td class="mono">{{ e.row }}</td>
            <td>{{ e.reason }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #footer>
      <el-button @click="onClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.row { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.tip { font-size: 12px; color: var(--vio-ink-3); }
.hidden { display: none; }
.fname { font-size: 12px; color: var(--vio-ink-3); }
.result { border-top: 1px solid var(--vio-rule-soft); padding-top: 12px; }
.sum { font-size: 13px; margin-bottom: 8px; }
.sum b { font-size: 16px; }
.sum .ok { color: var(--vio-ok); }
.sum .bad { color: var(--vio-seal); }
</style>
