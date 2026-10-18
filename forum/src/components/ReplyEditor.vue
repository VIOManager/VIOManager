<script setup lang="ts">
/**
 * 回复框（TopicPage 拆分件）：沉底回复盒。
 * 三种前置态互斥展示：LOCKED 置灰（后端 9101 兜底）/ 二级回复目标（parentReplyId，
 * 来自楼层"回复"）/ 引用目标（quoteReplyId，来自楼层"引用"——沿用原引用链路）。
 * 幂等口径不变：一张表单一个键，失败保留同键重试，成功后换新键。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiCreateReply, type ReplyItem } from '@/api/forum'

const props = defineProps<{
  topicId: string
  locked: boolean
  /** 二级回复目标：非空时提交带 parentReplyId（一级楼层） */
  parentTarget: ReplyItem | null
  /** 引用目标：非空时提交带 quoteReplyId（任意可见楼层） */
  quoteTarget: ReplyItem | null
}>()
const emit = defineEmits<{
  (e: 'replied'): void
  (e: 'clearParent'): void
  (e: 'clearQuote'): void
}>()

const replyText = ref('')
const submitting = ref(false)
const box = ref<HTMLTextAreaElement | null>(null)

/** 幂等键：失败保留（重试同键），成功换新键（回复下一楼是新内容） */
const formKey = ref(crypto.randomUUID())

/** 主楼"回复"按钮等外部唤起：滚到本框并聚焦 */
function focus() {
  if (box.value) {
    box.value.scrollIntoView({ behavior: 'smooth', block: 'center' })
    box.value.focus()
  }
}
defineExpose({ focus })

async function submit() {
  if (submitting.value || props.locked) return
  if (!replyText.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    await apiCreateReply(
      props.topicId,
      {
        text: replyText.value.trim(),
        quoteReplyId: props.quoteTarget ? props.quoteTarget.id : null,
        parentReplyId: props.parentTarget ? props.parentTarget.id : null
      },
      formKey.value
    )
    ElMessage.success('回复成功')
    replyText.value = ''
    formKey.value = crypto.randomUUID()
    emit('replied')
  } catch {
    // 失败：内容与 formKey 都保留，改完再点是同一次提交
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="box replybox" id="reply">
    <div class="in">
      <h5>添加一条新回复</h5>
      <div v-if="locked" class="q-tip">🔒 主题已被管理员锁定，暂停回复</div>
      <div v-else-if="parentTarget" class="q-tip">
        回复 @{{ parentTarget.author }} 的 #{{ parentTarget.floorNo }} 楼（将挂为其二级评论）
        <a href="#" @click.prevent="emit('clearParent')">取消</a>
      </div>
      <div v-else-if="quoteTarget" class="q-tip">
        引用 @{{ quoteTarget.author }} #{{ quoteTarget.floorNo }}
        <a href="#" @click.prevent="emit('clearQuote')">取消</a>
      </div>
      <textarea ref="box" v-model="replyText" :disabled="submitting || locked" placeholder="回复即留痕，删除需管理员操作"></textarea>
      <div class="r-foot">
        <i>※ 文明发言，排障类回复请尽量附现象、配置与波形</i>
        <button class="btn" type="button" :disabled="submitting || locked" @click="submit">
          {{ submitting ? '回复中…' : '回复' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 从 TopicPage 迁入的回复盒专属样式（其余皮肤走 skin.css） */
.replybox .in { padding: 16px 18px 18px; }
.replybox h5 { font-size: 13px; font-weight: 900; letter-spacing: 0.1em; margin-bottom: 10px; }
.q-tip { font-size: 12px; color: var(--vio2-ink-2); background: var(--vio2-panel-2); border-left: 4px solid var(--vio2-lilac); padding: 5px 10px; margin-bottom: 8px; font-weight: 600; }
.q-tip a { color: var(--vio2-ink-3); margin-left: 8px; }
.replybox textarea { width: 100%; height: 110px; border: var(--vio2-bd-main); background: var(--vio2-panel-2); color: var(--vio2-ink); padding: 11px; font: inherit; font-size: 13px; line-height: 1.7; resize: vertical; }
.replybox textarea:focus { outline: none; border-color: var(--vio2-blue); }
.r-foot { display: flex; align-items: center; margin-top: 12px; gap: 14px; }
.r-foot i { font-style: normal; font-size: 11.5px; color: var(--vio2-ink-3); flex: 1; font-family: var(--vio2-mono); }
</style>
