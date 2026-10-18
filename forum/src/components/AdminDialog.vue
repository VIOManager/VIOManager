<script setup lang="ts">
/**
 * 管理档统一弹窗（拍板：主题/楼层一个弹窗，字段动态切换）。
 * 编辑区：主题（标题/正文/标签 F11）/ 楼层（正文 F13），覆盖原文、edited 透出；
 * 状态区：主题 NORMAL/LOCKED/HIDDEN（F12）/ 楼层 NORMAL/HIDDEN（F14）——HIDDEN 是软删可恢复，
 * 弹二次确认；非法 to 值前端白名单先拦（后端 1001 兜底，2026-10-17 拍板口径）。
 * 幂等：编辑一张表单一个键（失败同键重试）；状态型操作每次点击新键。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  apiAdminUpdateTopic, apiAdminSetTopicStatus, apiAdminUpdateReply, apiAdminSetReplyStatus
} from '@/api/forum'

/** 弹窗目标：TopicPage 从 detail / 楼层对象拼好传入 */
export interface AdminTarget {
  type: 'topic' | 'reply'
  id: string
  /** 展示标签："主题 #9001" / "#5 楼" */
  label: string
  status: string
  title?: string
  bodyText?: string
  tags?: string[]
  text?: string
}

const props = defineProps<{ modelValue: boolean; target: AdminTarget | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'done'): void }>()

const title = ref('')
const bodyText = ref('')
const tagsText = ref('')
const text = ref('')
const saving = ref(false)
const statusBusy = ref(false)
/** 编辑表单幂等键：每次打开新生成，失败保留同键重试（PostDialog 同款约定） */
const formKey = ref('')

watch(
  () => props.modelValue,
  (v) => {
    if (v && props.target) {
      title.value = props.target.title ?? ''
      bodyText.value = props.target.bodyText ?? ''
      tagsText.value = (props.target.tags ?? []).join(', ')
      text.value = props.target.text ?? ''
      saving.value = false
      statusBusy.value = false
      formKey.value = crypto.randomUUID()
    }
  }
)

/** 状态按钮集（白名单即前端校验：主题三态、楼层两态，当前态禁用） */
const statusOptions = computed(() =>
  props.target?.type === 'reply'
    ? [
        { to: 'NORMAL', name: '恢复正常' },
        { to: 'HIDDEN', name: '隐藏（软删）' }
      ]
    : [
        { to: 'NORMAL', name: '恢复正常' },
        { to: 'LOCKED', name: '锁定禁回' },
        { to: 'HIDDEN', name: '隐藏（软删）' }
      ]
)

function close() {
  if (saving.value || statusBusy.value) return
  emit('update:modelValue', false)
}

function parseTags(): string[] {
  const tags: string[] = []
  const parts = tagsText.value.split(/[,，]/)
  for (let i = 0; i < parts.length; i++) {
    if (parts[i].trim()) tags.push(parts[i].trim())
  }
  return tags
}

async function saveEdit() {
  if (!props.target || saving.value) return
  if (props.target.type === 'topic') {
    if (!title.value.trim() || !bodyText.value.trim()) {
      ElMessage.warning('标题和正文都要填')
      return
    }
    saving.value = true
    try {
      await apiAdminUpdateTopic(
        props.target.id,
        { title: title.value.trim(), bodyText: bodyText.value.trim(), tags: parseTags() },
        formKey.value
      )
      ElMessage.success('主题已更新')
      emit('done')
      emit('update:modelValue', false)
    } catch {
      saving.value = false
    }
  } else {
    if (!text.value.trim()) {
      ElMessage.warning('评论内容不能为空')
      return
    }
    saving.value = true
    try {
      await apiAdminUpdateReply(props.target.id, text.value.trim(), formKey.value)
      ElMessage.success('评论已更新（已标注编辑）')
      emit('done')
      emit('update:modelValue', false)
    } catch {
      saving.value = false
    }
  }
}

async function setStatus(to: string) {
  if (!props.target || statusBusy.value || to === props.target.status) return
  if (to === 'HIDDEN') {
    try {
      await ElMessageBox.confirm('隐藏后学生不可见（软删，学生侧楼层显示占位文本），可随时恢复。确认隐藏？', '确认隐藏', { type: 'warning' })
    } catch {
      return
    }
  }
  statusBusy.value = true
  try {
    // 状态型操作：每次点击都是独立提交，用新幂等键
    if (props.target.type === 'topic') {
      await apiAdminSetTopicStatus(props.target.id, to as 'NORMAL' | 'LOCKED' | 'HIDDEN', crypto.randomUUID())
      ElMessage.success(to === 'HIDDEN' ? '已隐藏，可在首页"含隐藏"列表中恢复' : '主题状态已更新')
    } else {
      await apiAdminSetReplyStatus(props.target.id, to as 'NORMAL' | 'HIDDEN', crypto.randomUUID())
      ElMessage.success(to === 'HIDDEN' ? '楼层已隐藏（学生侧显示占位），可恢复' : '楼层状态已更新')
    }
    emit('done')
    emit('update:modelValue', false)
  } catch {
    // 拦截器已弹错误提示
  } finally {
    statusBusy.value = false
  }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue && target" class="overlay" @click.self="close">
      <div class="dlg">
        <h3>管理：{{ target.label }}<span class="cur">当前 {{ target.status }}</span></h3>

        <!-- 编辑区：主题与楼层字段不同，同一表单风格 -->
        <template v-if="target.type === 'topic'">
          <div class="row">
            <label>标题</label>
            <input v-model="title" maxlength="80" :disabled="saving" placeholder="一句话说清主题（80 字内）">
          </div>
          <div class="row">
            <label>正文</label>
            <textarea v-model="bodyText" rows="6" :disabled="saving" placeholder="回车分段（一段一行），段落会在详情页按段渲染"></textarea>
          </div>
          <div class="row">
            <label>标签</label>
            <input v-model="tagsText" :disabled="saving" placeholder="逗号分隔，如：STM32, 电机控制（可空）">
          </div>
        </template>
        <template v-else>
          <div class="row">
            <label>评论</label>
            <textarea v-model="text" rows="6" :disabled="saving" placeholder="覆盖原文；保存后 edited 标注透出"></textarea>
          </div>
        </template>
        <div class="foot">
          <i>管理员编辑覆盖原文并标注 edited（P4 拍板：透明度）</i>
          <span>
            <button class="btn" type="button" :disabled="saving || statusBusy" @click="close">取消</button>
            <button class="btn main" type="button" :disabled="saving || statusBusy" @click="saveEdit">
              {{ saving ? '保存中…' : '保存编辑' }}
            </button>
          </span>
        </div>

        <div class="divider"></div>

        <!-- 状态区：当前态禁用；HIDDEN 走二次确认 -->
        <div class="st">
          <label>状态操作</label>
          <div class="st-row">
            <button
              v-for="st in statusOptions"
              :key="st.to"
              class="btn"
              :class="{ cur: target.status === st.to }"
              type="button"
              :disabled="statusBusy || saving || target.status === st.to"
              @click="setStatus(st.to)"
            >{{ st.name }}</button>
          </div>
          <i v-if="statusBusy" class="st-tip">操作中…</i>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
/* 弹窗皮与 PostDialog 同款（遮罩/卡片/表单行/按钮），另加状态区 */
.overlay { position: fixed; inset: 0; background: var(--vio2-mask); display: grid; place-items: center; z-index: 60; }
.dlg { width: 560px; max-width: calc(100vw - 32px); background: var(--vio2-panel); border: var(--vio2-bd-main); box-shadow: var(--vio2-hard); padding: 22px 24px 18px; }
.dlg h3 { font-size: 17px; color: var(--vio2-ink); font-weight: 900; margin-bottom: 16px; padding-bottom: 12px; border-bottom: var(--vio2-bd-main); letter-spacing: 0.02em; }
.dlg h3 .cur { margin-left: 10px; font-size: 11px; font-family: var(--vio2-mono); color: var(--vio2-ink-3); font-weight: 700; }
.row { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.row > label:first-child { width: 42px; flex: none; font-family: var(--vio2-mono); font-size: 12px; color: var(--vio2-ink-3); padding-top: 7px; }
.row input, .row textarea { flex: 1; border: 2px solid var(--vio2-bd); padding: 7px 10px; font-size: 13px; font-family: inherit; background: var(--vio2-panel-2); color: var(--vio2-ink); }
.row input:focus, .row textarea:focus { outline: none; border-color: var(--vio2-blue); background: var(--vio2-panel); }
.row textarea { resize: vertical; line-height: 1.7; }
.foot { display: flex; align-items: center; border-top: 2px solid var(--vio2-row-line); padding-top: 12px; }
.foot i { flex: 1; font-style: normal; font-size: 12px; color: var(--vio2-ink-3); }
.btn { font-size: 13px; color: var(--vio2-ink-2); background: var(--vio2-panel); border: var(--vio2-bd-row); font-weight: 700; padding: 6px 16px; cursor: pointer; margin-left: 8px; box-shadow: var(--vio2-hard-xs); }
.btn:hover:not(:disabled) { transform: translate(2px, 2px); box-shadow: 1px 1px 0 var(--vio2-shadow); }
.btn.main { background: var(--vio2-yellow); color: var(--vio2-on-ac); border: var(--vio2-bd-acc); }
.btn.cur { background: var(--vio2-lilac); color: var(--vio2-ink); }
.btn:disabled { opacity: 0.55; cursor: not-allowed; }
.divider { height: 2px; background: var(--vio2-row-line); margin: 16px 0 12px; }
.st > label { display: block; font-family: var(--vio2-mono); font-size: 12px; color: var(--vio2-ink-3); margin-bottom: 8px; }
.st-row { display: flex; gap: 4px; }
.st-row .btn { margin-left: 0; margin-right: 8px; }
.st-tip { display: block; margin-top: 8px; font-style: normal; font-size: 11.5px; color: var(--vio2-ink-3); font-family: var(--vio2-mono); }
</style>
