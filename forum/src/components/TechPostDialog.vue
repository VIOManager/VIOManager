<script setup lang="ts">
/**
 * 管理员录入推送弹窗（FeedPage 顶部"录入推送"唤起，仅超管可见入口）
 * 契约：技术交流接口清单 T10 POST /api/tech-posts（带幂等头，9002 仅管理员）
 * ⚠️ 防重复提交：同 PostDialog——submitting 置灰 + formKey 一张表单一个键，失败重试同键
 */
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { FEED_CATEGORIES } from '@/config/feedCategories'
import { CONTENT_TYPE_LABEL } from '@/config/labels'
import { apiCreateTechPost } from '@/api/tech'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'created', id: string): void }>()

const title = ref('')
const summary = ref('')
const contentType = ref('ARTICLE')
const category = ref('EE')
const contentText = ref('')
const tagsText = ref('')
const linkUrl = ref('')
const pinned = ref(false)
const submitting = ref(false)

/** 幂等键：打开新生成，失败保留重试同键，成功随关闭作废（见 PostDialog 同款注释） */
const formKey = ref('')

function open() {
  title.value = ''
  summary.value = ''
  contentType.value = 'ARTICLE'
  category.value = 'EE'
  contentText.value = ''
  tagsText.value = ''
  linkUrl.value = ''
  pinned.value = false
  submitting.value = false
  formKey.value = crypto.randomUUID()
}

watch(
  () => props.modelValue,
  (v) => {
    if (v) open()
  }
)

function close() {
  if (submitting.value) return
  emit('update:modelValue', false)
}

async function submit() {
  if (submitting.value) return
  if (!title.value.trim() || !summary.value.trim()) {
    ElMessage.warning('标题和摘要都要填')
    return
  }
  if (contentType.value === 'LINK' && !linkUrl.value.trim()) {
    ElMessage.warning('外链类型必须填写链接地址')
    return
  }
  submitting.value = true
  try {
    const tags: string[] = []
    const parts = tagsText.value.split(/[,，]/)
    for (let i = 0; i < parts.length; i++) {
      if (parts[i].trim()) tags.push(parts[i].trim())
    }
    const res = await apiCreateTechPost(
      {
        title: title.value.trim(),
        summary: summary.value.trim(),
        content: contentText.value.trim(),
        contentType: contentType.value,
        category: category.value,
        tags,
        linkUrl: linkUrl.value.trim(),
        pinned: pinned.value
      },
      formKey.value
    )
    ElMessage.success('录入成功')
    emit('created', res.id)
    emit('update:modelValue', false)
  } catch {
    submitting.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="overlay" @click.self="close">
      <div class="dlg">
        <h3>录入技术推送</h3>
        <div class="row">
          <label>标题</label>
          <input v-model="title" maxlength="80" :disabled="submitting">
        </div>
        <div class="row">
          <label>摘要</label>
          <textarea v-model="summary" rows="2" maxlength="140" placeholder="列表页单行展示的摘要（140 字内）" :disabled="submitting"></textarea>
        </div>
        <div class="row">
          <label>形态</label>
          <select v-model="contentType" :disabled="submitting">
            <option v-for="(v, k) in CONTENT_TYPE_LABEL" :key="k" :value="k">{{ v }}</option>
          </select>
          <label>分类</label>
          <select v-model="category" :disabled="submitting" style="flex: 1">
            <option v-for="c in FEED_CATEGORIES" :key="c.code" :value="c.code">{{ c.name }}</option>
          </select>
        </div>
        <div class="row">
          <label>标签</label>
          <input v-model="tagsText" placeholder="逗号分隔，如：STM32, PID（可空）" :disabled="submitting">
        </div>
        <div v-if="contentType === 'LINK'" class="row">
          <label>链接</label>
          <input v-model="linkUrl" placeholder="https://…（外链类型必填）" :disabled="submitting">
        </div>
        <div v-else class="row">
          <label>正文</label>
          <textarea v-model="contentText" rows="5" placeholder="空行分段，详情页按段渲染" :disabled="submitting"></textarea>
        </div>
        <div class="row">
          <label>置顶</label>
          <label class="chk"><input type="checkbox" v-model="pinned" :disabled="submitting"> 置顶展示</label>
        </div>
        <div class="foot">
          <i>※ 来源记为"管理员录入"；RSS/AI 来源由论坛服务内部任务产出，不走本表单</i>
          <span>
            <button class="btn" type="button" :disabled="submitting" @click="close">取消</button>
            <button class="btn main" type="button" :disabled="submitting" @click="submit">
              {{ submitting ? '录入中…' : '录入' }}
            </button>
          </span>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 弹窗皮（2026-10-15 重涂）：与 PostDialog 同一块皮——遮罩 / 3px 粗边 / 硬投影 / 直角；逻辑零改动 */
.overlay { position: fixed; inset: 0; background: var(--vio2-mask); display: grid; place-items: center; z-index: 60; }
.dlg { width: 560px; max-width: calc(100vw - 32px); background: var(--vio2-panel); border: var(--vio2-bd-main); box-shadow: var(--vio2-hard); padding: 22px 24px 18px; }
.dlg h3 { font-size: 17px; color: var(--vio2-ink); font-weight: 900; margin-bottom: 16px; padding-bottom: 12px; border-bottom: var(--vio2-bd-main); letter-spacing: 0.02em; }
.row { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.row > label:first-child { width: 42px; flex: none; font-family: var(--vio2-mono); font-size: 12px; color: var(--vio2-ink-3); padding-top: 7px; }
.row select, .row input, .row textarea { flex: 1; border: 2px solid var(--vio2-bd); padding: 7px 10px; font-size: 13px; font-family: inherit; background: var(--vio2-panel-2); color: var(--vio2-ink); }
.row select:focus, .row input:focus, .row textarea:focus { outline: none; border-color: var(--vio2-blue); background: var(--vio2-panel); }
.row textarea { resize: vertical; line-height: 1.7; }
.chk { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--vio2-ink-2); padding-top: 6px; }
.foot { display: flex; align-items: center; border-top: 2px solid var(--vio2-row-line); padding-top: 12px; }
.foot i { flex: 1; font-style: normal; font-size: 12px; color: var(--vio2-ink-3); }
.btn { font-size: 13px; color: var(--vio2-ink-2); background: var(--vio2-panel); border: var(--vio2-bd-row); font-weight: 700; padding: 6px 16px; cursor: pointer; margin-left: 8px; box-shadow: var(--vio2-hard-xs); }
.btn:hover:not(:disabled) { transform: translate(2px, 2px); box-shadow: 1px 1px 0 var(--vio2-shadow); }
.btn.main { background: var(--vio2-yellow); color: var(--vio2-on-ac); border: var(--vio2-bd-acc); }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
</style>
