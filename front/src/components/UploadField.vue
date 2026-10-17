<script setup lang="ts">
/**
 * 通用上传组件（全站唯一文件上传入口：证件照/签名/材料/佐证都走它）
 * 对应方案：《前端ABC模块页面开发-优化方案》FS4
 * 机制：每次选择文件即生成一个 X-Idempotency-Key（规范 §6 幂等，重复点击/重试不会重复入库）；
 *    上传中按钮禁用；结果展示安全扫描状态（1010 拒绝由 http 拦截器统一弹提示）
 * ⚠️ 安全：accept/maxMb 只是前置拦截，真正的安全校验以后端扫描结果（1010）为准
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiUploadFile } from '@/api/file'
import type { UploadResp } from '@/api/file'

const props = withDefaults(
  defineProps<{
    /** 允许扩展名白名单，如 ".jpg,.png" */
    accept?: string
    /** 辅助说明（格式/大小/命名规则） */
    tip?: string
    maxMb?: number
    buttonText?: string
    /** 业务来源标识（大写蛇形，后端文件服务必填：对象存储目录分层 + 审计记录） */
    bizType: string
  }>(),
  { accept: '', tip: '', maxMb: 10, buttonText: '选择文件上传' }
)

const emit = defineEmits<{ uploaded: [file: UploadResp] }>()

const inputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
/** 已选文件信息（选择即生成幂等键并立即上传） */
const picked = ref<{ name: string; size: number } | null>(null)
const result = ref<UploadResp | null>(null)
const failed = ref(false)
let currentFile: File | null = null
let idemKey = ''

const sizeText = computed(() => {
  if (!picked.value) return ''
  const kb = picked.value.size / 1024
  return kb >= 1024 ? (kb / 1024).toFixed(1) + ' MB' : Math.max(Math.round(kb), 1) + ' KB'
})

function pick() {
  if (uploading.value) return
  inputRef.value?.click()
}

function onChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files && input.files[0]
  input.value = '' // 清空选择，允许重选同一文件再次触发 change
  if (!file) return
  if (file.size > props.maxMb * 1024 * 1024) {
    ElMessage.warning('文件超过 ' + props.maxMb + 'MB 限制')
    return
  }
  currentFile = file
  picked.value = { name: file.name, size: file.size }
  idemKey = crypto.randomUUID()
  result.value = null
  failed.value = false
  void doUpload()
}

async function doUpload() {
  if (!currentFile || uploading.value) return
  uploading.value = true
  try {
    const resp = await apiUploadFile(currentFile, idemKey, props.bizType)
    result.value = resp
    emit('uploaded', resp)
  } catch {
    // 1010 等提示已由 http 拦截器弹出；标记失败态，允许重选
    failed.value = true
  } finally {
    uploading.value = false
  }
}

function reset() {
  currentFile = null
  picked.value = null
  result.value = null
  failed.value = false
  idemKey = ''
}
</script>

<template>
  <div class="upload-field">
    <input ref="inputRef" type="file" :accept="accept" class="hidden-input" @change="onChange" />
    <button type="button" class="btn" :disabled="uploading" @click="pick">
      {{ uploading ? '上传扫描中…' : buttonText }}
    </button>
    <div v-if="picked" class="file-line">
      <span class="name">{{ picked.name }}</span>
      <span class="size mono">{{ sizeText }}</span>
      <span v-if="uploading" class="chip scan">扫描中</span>
      <span v-else-if="result" class="chip ok">安全扫描通过</span>
      <span v-else-if="failed" class="chip bad">扫描未通过</span>
      <button v-if="!uploading" type="button" class="link" @click="reset">移除</button>
    </div>
    <p v-if="tip" class="tip fs">{{ tip }}</p>
  </div>
</template>

<style scoped>
.hidden-input { display: none; }
.btn {
  padding: 7px 18px;
  background: var(--vio-sheet);
  border: 1px solid var(--vio-ink);
  color: var(--vio-ink);
  font-size: 13px;
  border-radius: 0;
  cursor: pointer;
  transition: all 0.25s var(--vio-ease);
}
.btn:hover:not(:disabled) { background: var(--vio-blue-ink); color: #F6F7F2; }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
.file-line { display: flex; align-items: center; gap: 10px; margin-top: 10px; font-size: 13px; }
.name { color: var(--vio-ink); max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.size { color: var(--vio-ink-3); font-size: 11.5px; }
.chip { font-size: 11px; padding: 1px 8px; letter-spacing: 0.06em; }
.chip.scan { color: var(--vio-warn); border: 1px solid var(--vio-warn); }
.chip.ok { color: var(--vio-ok); border: 1px solid var(--vio-ok); }
.chip.bad { color: var(--vio-seal); border: 1px solid var(--vio-seal); }
.link { background: none; border: none; color: var(--vio-ink-3); font-size: 12px; cursor: pointer; text-decoration: underline; }
.link:hover { color: var(--vio-seal); }
.tip { margin-top: 8px; font-size: 12px; color: var(--vio-ink-3); }
</style>
